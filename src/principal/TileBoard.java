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
 *   <li>which tile can be placed next to the tiles already present
 *       ({@link Options#pickOption(Random)}), and</li>
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

    private final TileSet set;
    private final int width;
    private final int height;
    private final int[][] cells;

    /**
     * Cached domain of every cell: the tiles that can be placed in it, or
     * {@code null} while the domain is unknown or the cell already holds a
     * tile. It is dropped whenever the cell or one of its four orthogonal
     * neighbours changes, and rebuilt the next time the domain is read.
     */
    private final int[][][] cachedDomains;

    /**
     * Live frontier: the placed cells that still have at least one empty
     * neighbour, i.e. exactly the cells the anchor can be chosen from.
     * {@link #writeCell(int, int, int)} keeps it in sync, so the solver never
     * has to sweep the whole board. The order is part of the reproducibility
     * contract: column-major, the same order the full scan used to visit, so
     * the RNG draws and therefore the board stay identical.
     */
    private final TreeSet<Point> frontier;

    /** Column-major order that mirrors the old {@code for x / for y} scan. */
    private static final Comparator<Point> COLUMN_MAJOR =
            Comparator.comparingInt(Point::x).thenComparingInt(Point::y);

    private int placedCount;

    /**
     * Reusable scratch that {@link #computeDomain(int, int)} builds the domain
     * in, sized to the tile set on first use. Nothing is ever leaked out of it:
     * the cached domain is the one trimmed copy the cell stores.
     */
    private int[] domainBuffer;

    /**
     * Names that an adjacent placed tile forbids repeating, one slot per side
     * in {@link Side#ALL} order, {@code null} when that direction bans nothing.
     * {@link #computeDomain(int, int)} rewrites every slot as it passes, and
     * only a fully written list ever reaches {@link #filterDomain(int)}.
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
        this.cachedDomains = new int[width][height][];
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
     * @throws IllegalArgumentException if the position is outside the board or
     *                                  the tile index does not exist
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
        if (cells[x][y] != EMPTY) {
            throw new IllegalStateException(
                    "Cell " + x + "," + y + " already holds tile " + cells[x][y]);
        }
        writeCell(x, y, tileIndex);
        placedCount++;
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
     * Writes a cell and drops the domains the change makes obsolete. It is the
     * only place where cells are written, which is what keeps the cached
     * domains honest: a domain depends on its own cell and its four orthogonal
     * neighbours, so exactly those five are invalidated here, and rebuilt from
     * the board the next time they are read.
     *
     * @param x         column
     * @param y         row
     * @param tileIndex tile index to store, or {@link #EMPTY} to clear the cell
     */
    private void writeCell(int x, int y, int tileIndex) {
        cells[x][y] = tileIndex;
        cachedDomains[x][y] = null;
        syncFrontierCell(x, y);
        for (Side side : Side.ALL) {
            int neighborX = x + side.dx();
            int neighborY = y + side.dy();
            if (inBounds(neighborX, neighborY)) {
                cachedDomains[neighborX][neighborY] = null;
                syncFrontierCell(neighborX, neighborY);
            }
        }
    }

    /**
     * Adds or removes a cell from the frontier so the set always holds exactly
     * the placed cells that still touch an empty neighbour. Only the written
     * cell and its four neighbours can change membership, so this is called on
     * those five after every write; clearing flows through the same path, which
     * is what makes backtracking re-add the neighbours whose gap came back.
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
     * What to do with one neighbour in {@link #forEachNeighbor(int, int,
     * NeighborVisitor)}: the state and the position of a cell the board
     * already knows is inside it.
     */
    interface NeighborVisitor {

        /**
         * @param empty whether the neighbour has no tile yet
         * @param x     column
         * @param y     row
         */
        void visit(boolean empty, int x, int y);
    }

    /**
     * Visits the orthogonal neighbours of a cell that are inside the board, in
     * {@link Side#ALL} order, telling the visitor whether each one is empty.
     * The scan reads the live cells, so a caller that modifies the board while
     * traversing has to collect what it needs first: a change made halfway can
     * flip the state reported to the neighbours visited after it.
     *
     * @param x       column
     * @param y       row
     * @param visitor what to do with each neighbour
     */
    void forEachNeighbor(int x, int y, NeighborVisitor visitor) {
        for (Side side : Side.ALL) {
            int neighborX = x + side.dx();
            int neighborY = y + side.dy();
            if (inBounds(neighborX, neighborY)) {
                visitor.visit(cells[neighborX][neighborY] == EMPTY, neighborX, neighborY);
            }
        }
    }

    /**
     * Opens a decision on a placed anchor tile, with nothing tried yet.
     *
     * @param anchorX column of the anchor tile
     * @param anchorY row of the anchor tile
     * @return the new decision
     */
    Options newOptions(int anchorX, int anchorY) {
        return new Options(this, anchorX, anchorY);
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
            int count = countPossiblePlacements(cell.x(), cell.y());
            if (count < lowest) {
                lowest = count;
                best = cell;
                ties = 1;
                if (count <= 1) {
                    // A forced cell or a dead one: nothing can score lower,
                    // no need to keep scanning.
                    return cell;
                }
            } else if (count == lowest) {
                ties++;
                if (random.nextInt(ties) == 0) {
                    // Reservoir sampling: keeps every tied candidate equally likely.
                    best = cell;
                }
            }
        }
        return best;
    }

    /**
     * Picks one of the occupied orthogonal neighbours of a cell at random, in
     * {@link Side#ALL} order, for the solver to clear when it breaks a deadlock.
     *
     * <p>Two plain passes over the four neighbours and no candidate list: the
     * first counts the occupied ones so the draw consumes the RNG exactly once,
     * and the second hands back the chosen one. Doing the count and the pick
     * here rather than in a {@link #forEachNeighbor} visitor is what lets both
     * passes use a plain local counter, since a lambda may only read
     * effectively-final locals and could not increment one.</p>
     *
     * @param x      column of the cell whose neighbours are inspected
     * @param y      row of the cell whose neighbours are inspected
     * @param random source used once to choose among the occupied neighbours
     * @return a random occupied neighbour, or {@code null} when there is none
     */
    Point randomOccupiedNeighbor(int x, int y, Random random) {
        int occupied = 0;
        for (Side side : Side.ALL) {
            int neighborX = x + side.dx();
            int neighborY = y + side.dy();
            if (inBounds(neighborX, neighborY) && cells[neighborX][neighborY] != EMPTY) {
                occupied++;
            }
        }
        if (occupied == 0) {
            return null;
        }
        int victim = random.nextInt(occupied);
        int seen = 0;
        for (Side side : Side.ALL) {
            int neighborX = x + side.dx();
            int neighborY = y + side.dy();
            if (inBounds(neighborX, neighborY) && cells[neighborX][neighborY] != EMPTY) {
                if (seen == victim) {
                    return new Point(neighborX, neighborY);
                }
                seen++;
            }
        }
        return null;
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
     * One decision of the solver: an anchor tile and the record of what has
     * been tried around it, kept on the backtrack stack so the decision can be
     * undone and continued later.
     *
     * <p>The options themselves are not stored: they are read live from the
     * board by {@link #pickOption(Random)} every time one is needed, so they
     * always reflect the current neighbours. What the decision keeps is the
     * list of {@code (side, tile)} pairs already tried, identified by value
     * so that a domain shrinking or growing around it can neither remap a
     * cursor onto an already tried tile nor resurrect a failed one. The last
     * pair of that list is the option the solver placed: {@link #x()},
     * {@link #y()} and {@link #tileIndex()} describe it so the solver can
     * undo exactly that placement. Every option handed out is placed before
     * the next is asked for.</p>
     */
    static final class Options {

        /** One option tried on one side of the anchor. */
        private record Placement(Side side, int tile) {
        }

        /**
         * The live domains of the anchor's four neighbours, seen as one flat
         * list in {@link Side#ALL} order. Built once per pick from the domains
         * as they are at that moment; resolving a slot to its
         * {@code (side, tile)} pair is what lets the picker probe a plain slot
         * range instead of walking four arrays.
         */
        private static final class Neighborhood {

            /** Domains of the four neighbours, in {@link Side#ALL} order. */
            private final int[][] domains = new int[Side.ALL.length][];
            /** Options the four domains hold together. */
            private int total;

            private Neighborhood(TileBoard board, int anchorX, int anchorY) {
                for (Side side : Side.ALL) {
                    int[] domain = board.domainAt(anchorX + side.dx(), anchorY + side.dy());
                    domains[side.ordinal()] = domain;
                    total += domain.length;
                }
            }

            /** Gets how many options the four domains hold together. */
            int size() {
                return total;
            }

            /**
             * Resolves a flat slot to the option it stands for.
             *
             * @param slot slot in {@code [0, size())}
             * @return the {@code (side, tile)} pair that slot holds
             */
            Placement placementAt(int slot) {
                for (Side side : Side.ALL) {
                    int[] domain = domains[side.ordinal()];
                    if (slot < domain.length) {
                        return new Placement(side, domain[slot]);
                    }
                    slot -= domain.length;
                }
                throw new IllegalArgumentException("Slot out of range: " + slot);
            }
        }

        /** Board whose live domains the options are read from. */
        private final TileBoard board;
        /** Column of the anchor the options hang off. */
        private final int anchorX;
        /** Row of the anchor the options hang off. */
        private final int anchorY;

        /** Pairs handed out so far, in order; the last one is the one placed. */
        private final List<Placement> tried = new ArrayList<>(4);

        private Options(TileBoard board, int anchorX, int anchorY) {
            this.board = board;
            this.anchorX = anchorX;
            this.anchorY = anchorY;
        }

        /**
         * Picks one tile to place in an empty neighbour of the anchor, at
         * random among the options the decision has not tried yet.
         *
         * <p>The domains are read live from the board, so the option always
         * fits the neighbours as they are now, not as they were when the
         * decision was taken. The four neighbour domains are gathered into a
         * {@link Neighborhood} flat list in {@link Side#ALL} order: a random
         * start is drawn once and the list is probed forward, wrapping
         * around, until an option the decision has not tried is found. The
         * chosen option is appended to the tried list, so it becomes the last
         * option handed out, which is the one the solver places and later
         * undoes.</p>
         *
         * @param random source used once to choose the probe start
         * @return {@code true} if an untried option was handed out,
         *         {@code false} when the anchor has no empty neighbour or
         *         every option is tried
         */
        boolean pickOption(Random random) {
            Neighborhood neighborhood = new Neighborhood(board, anchorX, anchorY);
            int total = neighborhood.size();
            if (total == 0) {
                return false;
            }
            int start = random.nextInt(total);
            for (int step = 0; step < total; step++) {
                Placement placement = neighborhood.placementAt((start + step) % total);
                if (!tried.contains(placement)) {
                    tried.add(placement);
                    return true;
                }
            }
            return false;
        }

        /** Gets the column the last option placed its tile in. */
        int x() {
            return anchorX + last().side().dx();
        }

        /** Gets the row the last option placed its tile in. */
        int y() {
            return anchorY + last().side().dy();
        }

        /** Gets the tile the last option placed. */
        int tileIndex() {
            return last().tile();
        }

        /** The option handed out last, which the solver placed. */
        private Placement last() {
            return tried.get(tried.size() - 1);
        }
    }

    /**
     * Gets the tiles that can go into a cell, computing them the first time
     * they are asked for.
     *
     * <p>Throwing a cached domain away is always safe: it is rebuilt from the
     * board with exactly the value it would have had if it had been kept up to
     * date.</p>
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
        int[] domain = cachedDomains[x][y];
        if (domain == null) {
            domain = computeDomain(x, y);
            cachedDomains[x][y] = domain;
        }
        return domain;
    }

    /**
     * Computes the domain of a cell by intersecting the ascending option lists
     * of its placed neighbours. Intersecting ascending arrays keeps the result
     * ascending, so it preserves the order of the set and a seed reproduces the
     * same board.
     *
     * <p>The two rules the compatibility index cannot express live elsewhere:
     * name bans are gathered per side and applied last by
     * {@link #filterDomain(int)}; the ground rule is left to the solver, since
     * a misplaced soil tile starves the cell below it and the contradiction
     * handling clears it again. No positional filter is needed.</p>
     *
     * @param x column
     * @param y row
     * @return compatible tiles, ordered as in the set
     */
    private int[] computeDomain(int x, int y) {
        if (domainBuffer == null) {
            domainBuffer = new int[set.size()];
        }
        int size = 0;
        boolean seeded = false;
        for (Side side : Side.ALL) {
            Tile neighbor = tileAt(x + side.dx(), y + side.dy());
            if (neighbor == null) {
                forbiddenNames[side.ordinal()] = null;
                continue;
            }
            // A placed tile that forbids its own name bans every tile sharing
            // it; the ban lives in the neighbour's side slot so the filter can
            // jump over a clean direction with one null check.
            forbiddenNames[side.ordinal()] = neighbor.forbidsSameNeighbor()
                    ? neighbor.getName() : null;
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
     * Drops every buffered candidate whose name a neighbouring tile bans, one
     * narrowing pass per ban; the passes commute because compaction keeps the
     * domain ascending. Returns the freshly sized copy the cached domain needs.
     */
    private int[] filterDomain(int size) {
        int remaining = size;
        for (String forbidden : forbiddenNames) {
            if (forbidden == null) {
                continue;
            }
            int kept = 0;
            for (int i = 0; i < remaining; i++) {
                int tileIndex = domainBuffer[i];
                if (!set.get(tileIndex).getName().equals(forbidden)) {
                    domainBuffer[kept++] = tileIndex;
                }
            }
            remaining = kept;
        }
        return remaining == 0 ? NO_TILES : Arrays.copyOf(domainBuffer, remaining);
    }

    private Tile tileAt(int x, int y) {
        if (!inBounds(x, y)) {
            return null;
        }
        int tileIndex = cells[x][y];
        return tileIndex == EMPTY ? null : set.get(tileIndex);
    }
}
