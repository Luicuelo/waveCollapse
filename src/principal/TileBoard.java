package principal;

import principal.Tile.Side;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.Random;
import java.util.TreeSet;

/**
 * The grid of tiles being generated.
 *
 * <p>A board stores one tile index per cell, or {@link #EMPTY} when the cell is
 * still undecided. It also answers the two questions the solver needs:</p>
 *
 * <ul>
 *   <li>which tiles can be placed next to the tiles already present
 *       ({@link #getPossiblePlacements(int, int, Random)}), and</li>
 *   <li>where it is best to keep growing ({@link #getLowestEntropyAnchor(Random)}).</li>
 * </ul>
 *
 * <p>Answering them by brute force would test every tile of the set against
 * every cell, over and over. Instead the board caches the domain of each cell,
 * the tiles that can still go in it: a change drops the domains of the cell and
 * of its four orthogonal neighbours, which are the only ones whose surroundings
 * changed, and they are rebuilt the next time they are read.</p>
 *
 * <p>The board is not thread safe by design: it belongs to exactly one
 * {@link WfcSolver} run, which is what keeps the whole generation free of
 * races. A board is never reused either: a new generation builds a new board,
 * so a run that is still dying out can never write into the board that is
 * already being drawn.</p>
 *
 * @see WfcSolver
 */
final class TileBoard {

    /** Value stored in a cell that has no tile yet. */
    static final int EMPTY = -1;

    /**
     * Domain of a cell where no tile fits. It is shared, so dead cells cost no
     * allocation at all.
     */
    private static final int[] NO_TILES = new int[0];

    /**
     * A cell of the board.
     *
     * @param x column
     * @param y row
     */
    record Point(int x, int y) {
        @Override
        public String toString() {
            return "(" + x + ", " + y + ")";
        }
    }

    /**
     * A tile that can be placed at a concrete empty cell.
     *
     * @param tileIndex index of the tile in the {@link TileSet}
     * @param x         column where the tile would be placed
     * @param y         row where the tile would be placed
     */
    record Placement(int tileIndex, int x, int y) {
    }

    private final TileSet set;
    private final int width;
    private final int height;
    private final int[][] cells;

    /**
     * Domain of every cell: the tiles that can be placed in it, or {@code null}
     * while the domain is unknown or the cell already holds a tile. It is
     * dropped whenever the cell or one of its four orthogonal neighbours
     * changes, and rebuilt the next time the domain is read.
     */
    private final int[][][] candidates;

    /**
     * Live frontier: the placed cells that still have at least one empty
     * neighbour, i.e. exactly the cells the anchor can be chosen from. It is
     * kept in sync by {@link #writeCell(int, int, int)} for the written cell and
     * its four neighbours, so the solver no longer has to sweep the whole board
     * every step to rediscover them. The set is ordered column-major (x, then y)
     * so iterating it visits the frontier in the very same order the full scan
     * used to, which preserves the RNG draw sequence and the reproduced board.
     */
    private final TreeSet<Point> frontier;

    /** Column-major order that mirrors the old {@code for x / for y} scan. */
    private static final Comparator<Point> COLUMN_MAJOR =
            Comparator.comparingInt(Point::x).thenComparingInt(Point::y);

    private int placedCount;

    /**
     * Reusable scratch that {@link #computeDomain(int, int)} accumulates the
     * domain in, sized to the tile set on first use. Seeding copies the first
     * neighbour's options here and later neighbours narrow it in place, so
     * building a domain allocates only the final array the cell caches. It is
     * never leaked out: the cached domain is a trimmed copy.
     */
    private int[] domainBuffer;

    /**
     * Names that an adjacent placed tile forbids repeating, gathered by
     * {@link #computeDomain(int, int)} on every call and read back by
     * {@link #filterDomain(int)}. At most one per neighbour.
     */
    private final String[] forbiddenNames = new String[Side.ALL.length];

    /**
     * Creates an empty board.
     *
     * @param set    tile set the tiles are taken from
     * @param width  number of columns
     * @param height number of rows
     * @throws IllegalArgumentException if any dimension is not positive
     */
    TileBoard(TileSet set, int width, int height) {
        if (width <= 0 || height <= 0) {
            throw new IllegalArgumentException(
                    "Board dimensions must be positive, got " + width + "x" + height);
        }
        this.set = set;
        this.width = width;
        this.height = height;
        this.cells = new int[width][height];
        for (int[] column : cells) {
            Arrays.fill(column, EMPTY);
        }
        this.candidates = new int[width][height][];
        this.frontier = new TreeSet<>(COLUMN_MAJOR);
    }

    /**
     * Gets the set this board takes its tiles from. The set is immutable once
     * loaded, so it can be shared with every other generation.
     *
     * @return the set of this board
     */
    TileSet getSet() {
        return set;
    }

    /**
     * Gets the number of columns.
     *
     * @return the board width in cells
     */
    int getWidth() {
        return width;
    }

    /**
     * Gets the number of rows.
     *
     * @return the board height in cells
     */
    int getHeight() {
        return height;
    }

    /**
     * Gets the tile placed in a cell.
     *
     * @param x column
     * @param y row
     * @return the tile index, or {@link #EMPTY} if the cell has no tile yet
     */
    int getTileIndex(int x, int y) {
        return cells[x][y];
    }

    /**
     * Tells whether a position lies inside the board.
     *
     * @param x column
     * @param y row
     * @return {@code true} if the position is inside the board
     */
    boolean inBounds(int x, int y) {
        return x >= 0 && x < width && y >= 0 && y < height;
    }

    /**
     * Tells whether a position belongs to the board and has no tile yet.
     *
     * @param x column
     * @param y row
     * @return {@code true} if the position is inside the board and empty
     */
    boolean isEmpty(int x, int y) {
        return inBounds(x, y) && cells[x][y] == EMPTY;
    }

    /**
     * Gets how many tiles are already placed.
     *
     * @return the number of placed tiles
     */
    int getPlacedCount() {
        return placedCount;
    }

    /**
     * Tells whether every cell holds a tile.
     *
     * @return {@code true} if the board is complete
     */
    boolean isComplete() {
        return placedCount == width * height;
    }

    /**
     * Places a tile in an empty cell.
     *
     * @param x         column
     * @param y         row
     * @param tileIndex index of the tile in the {@link TileSet}
     * @throws IllegalArgumentException if the position is outside the board, the
     *                                  tile index does not exist, or a bottom tile
     *                                  is placed outside the bottom row of a
     *                                  from-bottom set
     * @throws IllegalStateException    if the cell already has a tile; the
     *                                  previous content is never overwritten
     *                                  silently
     */
    void place(int x, int y, int tileIndex) {
        if (!inBounds(x, y)) {
            throw new IllegalArgumentException("Position " + x + "," + y + " is outside the board");
        }
        Tile tile = set.get(tileIndex);
        if (tile == null) {
            throw new IllegalArgumentException("Unknown tile index: " + tileIndex);
        }
        if (!allowsBottomAt(y) && tile.isBottomTile()) {
            throw new IllegalArgumentException("Bottom tiles can only be placed in the bottom row");
        }
        if (cells[x][y] != EMPTY) {
            throw new IllegalStateException(
                    "Cell " + x + "," + y + " already holds tile " + cells[x][y]);
        }
        writeCell(x, y, tileIndex);
        placedCount++;
    }

    /** Tells whether bottom tiles may be placed in the given row. */
    private boolean allowsBottomAt(int y) {
        return !set.startsFromBottom() || y == height - 1;
    }

    /**
     * Removes the tile of a cell. Clearing an empty or out of board cell does
     * nothing, which makes backtracking idempotent.
     *
     * @param x column
     * @param y row
     */
    void clear(int x, int y) {
        if (inBounds(x, y) && cells[x][y] != EMPTY) {
            writeCell(x, y, EMPTY);
            placedCount--;
        }
    }

    /**
     * Writes a cell and drops the domains the change makes obsolete.
     *
     * <p>It is the only place where the cells are written, which is what keeps
     * the cached domains honest: the domain of a cell depends on the tiles
     * placed in its four orthogonal neighbours, so those neighbours are the
     * only domains a change can invalidate. The cell itself loses its own
     * domain as well: while it holds a tile there is nothing to place in it, and
     * once it is cleared again its domain is likely out of date.</p>
     *
     * @param x         column
     * @param y         row
     * @param tileIndex tile index to store, or {@link #EMPTY} to clear the cell
     */
    private void writeCell(int x, int y, int tileIndex) {
        cells[x][y] = tileIndex;
        candidates[x][y] = null;
        syncFrontierCell(x, y);
        for (Side side : Side.ALL) {
            int neighborX = x + side.dx();
            int neighborY = y + side.dy();
            if (inBounds(neighborX, neighborY)) {
                candidates[neighborX][neighborY] = null;
                syncFrontierCell(neighborX, neighborY);
            }
        }
    }

    /**
     * Adds or removes a cell from the frontier so the set always holds exactly
     * the placed cells that still touch an empty neighbour.
     *
     * <p>Only the written cell and its four neighbours can change membership,
     * and their empty-neighbour status is the only thing that flipped, so this
     * is called on just those five cells after every write. Clearing a cell
     * flows through the same path, which is what makes backtracking re-add the
     * neighbours whose gap came back.</p>
     *
     * @param x column
     * @param y row
     */
    private void syncFrontierCell(int x, int y) {
        Point cell = new Point(x, y);
        if (cells[x][y] != EMPTY && hasEmptyNeighbor(x, y)) {
            frontier.add(cell);
        } else {
            frontier.remove(cell);
        }
    }

    /**
     * Gets the cell at the centre of the board.
     *
     * @return the centre position
     */
    Point getMiddle() {
        return new Point(width / 2, height / 2);
    }

    /**
     * Gets the empty neighbours of a cell.
     *
     * @param x column
     * @param y row
     * @return the empty neighbours, at most four
     */
    List<Point> getEmptyNeighbors(int x, int y) {
        List<Point> neighbors = new ArrayList<>(Side.ALL.length);
        for (Side side : Side.ALL) {
            int neighborX = x + side.dx();
            int neighborY = y + side.dy();
            if (inBounds(neighborX, neighborY) && cells[neighborX][neighborY] == EMPTY) {
                neighbors.add(new Point(neighborX, neighborY));
            }
        }
        return neighbors;
    }

    /**
     * Gets the neighbours of a cell that already hold a tile.
     *
     * @param x column
     * @param y row
     * @return the occupied neighbours, at most four
     */
    List<Point> getOccupiedNeighbors(int x, int y) {
        List<Point> neighbors = new ArrayList<>(Side.ALL.length);
        for (Side side : Side.ALL) {
            int neighborX = x + side.dx();
            int neighborY = y + side.dy();
            if (inBounds(neighborX, neighborY) && cells[neighborX][neighborY] != EMPTY) {
                neighbors.add(new Point(neighborX, neighborY));
            }
        }
        return neighbors;
    }

    /**
     * Lists every tile that can be placed in the empty cells around a tile, in a
     * reproducible random order so that the generation does not look biased.
     *
     * <p>The result is a lazy view over the cached domains of the neighbours:
     * nothing is concatenated and no placement object is built up front. Each
     * {@link PlacementOptions#next()} resolves one option on demand from the
     * neighbour arrays, so a step that never backtracks allocates only the single
     * {@link Placement} it actually consumes.</p>
     *
     * @param x      column of the tile used as anchor
     * @param y      row of the tile used as anchor
     * @param random source of randomness
     * @return the possible placements, empty when the anchor cannot grow
     */
    PlacementOptions getPossiblePlacements(int x, int y, Random random) {
        if (!inBounds(x, y) || cells[x][y] == EMPTY) {
            return PlacementOptions.EMPTY;
        }
        int sides = Side.ALL.length;
        int[][] domains = new int[sides][];
        int[] targetX = new int[sides];
        int[] targetY = new int[sides];
        int[] starts = new int[sides + 1];

        // Gather only the neighbours that still have options, keeping references
        // to their cached arrays. starts marks the virtual offset of each array.
        int count = 0;
        int running = 0;
        for (Side side : Side.ALL) {
            int neighborX = x + side.dx();
            int neighborY = y + side.dy();
            int[] domain = domainAt(neighborX, neighborY);
            if (domain.length == 0) {
                continue;
            }
            domains[count] = domain;
            targetX[count] = neighborX;
            targetY[count] = neighborY;
            starts[count] = running;
            running += domain.length;
            count++;
        }
        if (count == 0) {
            return PlacementOptions.EMPTY;
        }
        starts[count] = running;
        return new PlacementOptions(domains, targetX, targetY, starts, running,
                shuffledRange(running, random));
    }

    /**
     * Counts how many placements are possible around a tile. Used to measure the
     * entropy of a cell without building the whole list.
     *
     * <p>It only reads the cached domains of the empty neighbours, so no tile is
     * ever tested and no placement is ever created.</p>
     *
     * @param x column of the tile used as anchor
     * @param y row of the tile used as anchor
     * @return the number of possible placements
     */
    int countPossiblePlacements(int x, int y) {
        if (!inBounds(x, y) || cells[x][y] == EMPTY) {
            return 0;
        }
        int count = 0;
        for (Side side : Side.ALL) {
            count += domainAt(x + side.dx(), y + side.dy()).length;
        }
        return count;
    }

    /**
     * Finds the placed tile that has the fewest ways to grow, which is the one
     * the solver collapses next.
     *
     * <p>Ties are broken at random: without that, the scan order would always
     * favour the same corner and the pattern would grow noticeably towards
     * it.</p>
     *
     * @param random source of randomness
     * @return the chosen anchor, or {@code null} when no placed tile has an empty
     *         neighbour (which means the board is empty or complete)
     */
    Point getLowestEntropyAnchor(Random random) {
        Point best = null;
        int lowest = Integer.MAX_VALUE;
        int ties = 0;

        // The frontier already holds exactly the placed cells with an empty
        // neighbour, in column-major order, so this replaces the whole-board
        // sweep with a pass over the few cells that can actually be the anchor.
        for (Point cell : frontier) {
            int options = countPossiblePlacements(cell.x(), cell.y());
            if (options < lowest ) {
                lowest = options;
                best = cell;
                ties = 1;
                if (options == 1) {
                    // Forced cell: nothing can score lower, no need to keep scanning.
                    return cell;
                }
            } else if (options == lowest && random.nextInt(++ties) == 0) {
                // Reservoir sampling: keeps every tied candidate equally likely.
                best = cell;
            }
        }
        return best;
    }

    private boolean hasEmptyNeighbor(int x, int y) {
        for (Side side : Side.ALL) {
            int neighborX = x + side.dx();
            int neighborY = y + side.dy();
            if (inBounds(neighborX, neighborY) && cells[neighborX][neighborY] == EMPTY) {
                return true;
            }
        }
        return false;
    }

    /**
     * Builds a random permutation of {@code 0..size-1} with Fisher-Yates, so the
     * options are visited in a reproducible shuffled order without ever moving
     * the domain arrays themselves.
     *
     * @param size   number of slots to permute
     * @param random source of randomness
     * @return the shuffled visit order
     */
    private static int[] shuffledRange(int size, Random random) {
        int[] order = new int[size];
        for (int i = 0; i < size; i++) {
            order[i] = i;
        }
        for (int i = size - 1; i > 0; i--) {
            int j = random.nextInt(i + 1);
            int swap = order[i];
            order[i] = order[j];
            order[j] = swap;
        }
        return order;
    }

    /**
     * A lazy, shuffled view over the domains of the empty neighbours of one
     * anchor. The neighbour option arrays are referenced, not concatenated, and
     * {@code starts} marks where each array would begin in a virtual flat list.
     * {@link #next()} consumes the pre-shuffled {@code order} and materialises a
     * single {@link Placement} per call, so building the options costs no
     * per-option allocation and the RNG is only touched once per anchor.
     */
    static final class PlacementOptions {

        /** Shared empty result for an anchor that cannot grow at all. */
        static final PlacementOptions EMPTY = new PlacementOptions(
                new int[0][], new int[0], new int[0], new int[1], 0, new int[0]);

        private final int[][] domains;
        private final int[] targetX;
        private final int[] targetY;
        private final int[] starts;
        private final int total;
        private final int[] order;
        private int cursor;

        PlacementOptions(int[][] domains, int[] targetX, int[] targetY, int[] starts,
                         int total, int[] order) {
            this.domains = domains;
            this.targetX = targetX;
            this.targetY = targetY;
            this.starts = starts;
            this.total = total;
            this.order = order;
        }

        /** Tells whether the anchor has no way to grow. */
        boolean isEmpty() {
            return total == 0;
        }

        /** Gets how many placements the anchor can still make. */
        int size() {
            return total;
        }

        /**
         * Resolves the next option in shuffled order, mapping the flat slot to
         * its neighbour array and offset on the fly.
         *
         * @return the placement, or {@code null} when every option was consumed
         */
        Placement next() {
            if (cursor >= total) {
                return null;
            }
            int slot = order[cursor++];
            int side = 0;
            while (starts[side + 1] <= slot) {
                side++;
            }
            int tileIndex = domains[side][slot - starts[side]];
            return new Placement(tileIndex, targetX[side], targetY[side]);
        }
    }

    /**
     * Gets the tiles that can go into a cell, computing them the first time they
     * are asked for.
     *
     * <p>A domain depends on the cell's fixed position and the tiles placed
     * around it, so throwing it away is always safe: it is rebuilt from the
     * board when it is needed again, with exactly the value it would have had if
     * it had been kept up to date.</p>
     *
     * @param x column
     * @param y row
     * @return the domain of the cell, or nothing when it is outside the board or
     *         already holds a tile
     */
    private int[] domainAt(int x, int y) {
        if (!inBounds(x, y) || cells[x][y] != EMPTY) {
            return NO_TILES;
        }
        int[] domain = candidates[x][y];
        if (domain == null) {
            domain = computeDomain(x, y);
            candidates[x][y] = domain;
        }
        return domain;
    }

    /**
     * Computes the domain of a cell by merging the ascending option lists of its
     * placed neighbours. Intersecting ascending arrays keeps the result
     * ascending, so it preserves the order of the set and a seed reproduces the
     * same board. The neighbour-name rule is applied last, because the side index
     * cannot express it. Bottom tiles need no positional check here: they carry no
     * entry under the neighbour-facing {@code TOP} side, so the index only ever
     * offers them for the last row, where {@code place} also accepts them.
     *
     * @param x column
     * @param y row
     * @return compatible tiles, ordered as in the set
     */
    private int[] computeDomain(int x, int y) {
        if (domainBuffer == null) {
            domainBuffer = new int[set.size()];
        }
        // Mark the ban list empty; only the stop marker matters, the tail is junk.
        forbiddenNames[0] = null;
        int size = 0;
        int forbidden = 0;
        boolean seeded = false;
        for (Side side : Side.ALL) {
            Tile neighbor = tileAt(x + side.dx(), y + side.dy());
            if (neighbor == null) {
                continue;
            }
            // A placed tile that forbids its own name bans every tile sharing it.
            if (neighbor.forbidsSameNeighbor()) {
                forbiddenNames[forbidden++] = neighbor.getName();
                if (forbidden < forbiddenNames.length) {
                    // Keep the next slot as the stop marker the filter scans for.
                    forbiddenNames[forbidden] = null;
                }
            }
            Side facing = side.opposite();
            String signature = new String(facing.zonesOf(neighbor));
            int[] options = set.compatibleTiles(facing, signature);
            if (options.length == 0) {
                return NO_TILES;
            }
            // The first neighbour fills the buffer, the rest narrow it in place.
            size = seeded ? intersectInto(size, options) : fill(options);
            seeded = true;
            if (size == 0) {
                return NO_TILES;
            }
        }
        if (!seeded) {
            // An isolated cell simply takes every tile the set holds.
            size = fill(set.allTileIndices());
        }
        return filterDomain(size);
    }

    /**
     * Copies an ascending option list into {@link #domainBuffer} as the starting
     * domain.
     *
     * @param source ascending values to seed with
     * @return the number of values now in the buffer
     */
    private int fill(int[] source) {
        System.arraycopy(source, 0, domainBuffer, 0, source.length);
        return source.length;
    }

    /**
     * Narrows the values held in {@link #domainBuffer}{@code [0..size)} to those
     * also present in the ascending {@code options}, compacting in place. Overwriting
     * the buffer is safe because it stays ascending and the write index never
     * passes the read index, while {@code options} is a separate read-only array.
     *
     * @param size    live values currently in the buffer
     * @param options ascending values to intersect with
     * @return the number of surviving values
     */
    private int intersectInto(int size, int[] options) {
        int i = 0;
        int j = 0;
        int kept = 0;
        while (i < size && j < options.length) {
            int left = domainBuffer[i];
            int right = options[j];
            if (left == right) {
                domainBuffer[kept++] = left;
                i++;
                j++;
            } else if (left < right) {
                i++;
            } else {
                j++;
            }
        }
        return kept;
    }

    /**
     * Drops the tiles whose name an adjacent placed tile bans, keeping the order.
     * Compacts the survivors to the front of the buffer and returns a freshly
     * sized copy: the one array the cached domain needs.
     */
    private int[] filterDomain(int size) {
        int kept = 0;
        for (int i = 0; i < size; i++) {
            int tileIndex = domainBuffer[i];
            if (!nameIsForbidden(set.get(tileIndex).getName())) {
                domainBuffer[kept++] = tileIndex;
            }
        }
        return kept == 0 ? NO_TILES : Arrays.copyOf(domainBuffer, kept);
    }

    /**
     * Tells whether a candidate name is banned by some neighbouring tile, scanning
     * the null-terminated ban list gathered by {@link #computeDomain(int, int)}. A
     * shared name always carries the same forbid flag, so matching the name of a
     * forbidding neighbour is enough.
     */
    private boolean nameIsForbidden(String name) {
        for (String forbidden : forbiddenNames) {
            if (forbidden == null) {
                break;
            }
            if (forbidden.equals(name)) {
                return true;
            }
        }
        return false;
    }

    private Tile tileAt(int x, int y) {
        if (!inBounds(x, y)) {
            return null;
        }
        int tileIndex = cells[x][y];
        return tileIndex == EMPTY ? null : set.get(tileIndex);
    }
}
