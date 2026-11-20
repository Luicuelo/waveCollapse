package principal;

import principal.Tile.Side;

import java.util.Arrays;
import java.util.Comparator;
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
     * Collects every tile that can be placed in the empty cells around an
     * anchor, in a reproducible random order so that the generation does not
     * look biased.
     *
     * <p>The build reads exactly the four cached neighbour domains and hands
     * them to {@link Options} as references, one per side: nothing is
     * concatenated and no per-option object is built up front.</p>
     *
     * @param x      column of the tile used as anchor
     * @param y      row of the tile used as anchor
     * @param random source used once to shuffle the visit order
     * @return the possible placements, empty when the anchor cannot grow
     */
    Options getPossiblePlacements(int x, int y, Random random) {
        if (!inBounds(x, y) || cells[x][y] == EMPTY) {
            return Options.EMPTY;
        }
        Options options = new Options(x, y);
        for (Side side : Side.ALL) {
            options.addDomain(side, domainAt(x + side.dx(), y + side.dy()));
        }
        if (options.isEmpty()) {
            return Options.EMPTY;
        }
        options.shuffleVisitOrder(random);
        return options;
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
                if (count == 1) {
                    // Forced cell: nothing can score lower, no need to keep scanning.
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
     * Everything that could be placed around one anchor when the decision was
     * taken: the tiles its empty neighbours admit, seen as one flat list of
     * options tried in a shuffled order.
     *
     * <p>One domain per side is stored in {@link Side#ALL} order, so the flat
     * list is just them concatenated: if the TOP neighbour admits 7 tiles and
     * the BOTTOM one admits 4, options 0..6 are the TOP tiles and options 7..10
     * are the BOTTOM ones, and option 9 is the third tile of the BOTTOM domain.
     * {@link #next()} walks the domains subtracting lengths until the option
     * falls inside one of them, records its side and tile, and the cell to
     * fill is then the anchor shifted by that side's offset.</p>
     *
     * <p>The object is also the record of its own cursor: {@link #x()},
     * {@link #y()} and {@link #tileIndex()} describe the option
     * {@code next()} handed out last, so the solver can place it, leave the
     * view on the backtrack stack and, much later, undo exactly that
     * placement. This only holds while the caller places every option it
     * takes before asking for the next one: peeking or discarding without
     * placing would overwrite the record unnoticed.</p>
     *
     * <p>The domains are references to the cached arrays of the board, never
     * copies: a domain only changes by being replaced with a new array, so this
     * object keeps the exact options the decision was taken with even while it
     * waits in the solver's stack and the board changes around it.</p>
     */
    static final class Options {

        /** Shared result for an anchor that cannot grow at all. */
        static final Options EMPTY = new Options(-1, -1);

        /** Column of the anchor the domains hang off. */
        private final int anchorX;
        /** Row of the anchor the domains hang off. */
        private final int anchorY;

        /** Tiles admitted per side, indexed by side; never {@code null}. */
        private final int[][] domains = new int[Side.ALL.length][];

        /** Total options: the summed lengths of every domain. */
        private int total;

        /** Shuffled visit order of the {@code 0..total-1} options. */
        private int[] visitOrder = new int[0];

        /** How many options have already been handed out. */
        private int cursor;

        /** Side of the neighbour cell the current option fills. */
        private Side currentSide;
        /** Tile the current option would place in that cell. */
        private int currentTile;

        Options(int anchorX, int anchorY) {
            this.anchorX = anchorX;
            this.anchorY = anchorY;
            Arrays.fill(domains, NO_TILES);
        }

        /**
         * Records the tiles admitted by the neighbour cell on one side.
         *
         * @param side   side of the anchor where that neighbour sits
         * @param domain the tiles allowed there, referenced, not copied
         */
        void addDomain(Side side, int[] domain) {
            domains[side.ordinal()] = domain;
            total += domain.length;
        }

        /**
         * Fixes the random order in which the options will be tried, so the
         * growth does not favour the first side over and over. Called once,
         * after all domains are added, and never when there is nothing to
         * visit.
         *
         * @param random source of randomness
         */
        void shuffleVisitOrder(Random random) {
            visitOrder = shuffledRange(total, random);
        }

        /** Tells whether the anchor has no way to grow. */
        boolean isEmpty() {
            return total == 0;
        }

        /**
         * Advances to the next option in visit order: walks the domains in
         * side order, subtracting their lengths until the option falls inside
         * one, and records it as the current option.
         *
         * @return {@code true} if an option was handed out, {@code false} when
         *         the list was fully consumed
         */
        boolean next() {
            if (cursor >= total) {
                return false;
            }
            int option = visitOrder[cursor++];
            int slot = option;
            for (Side side : Side.ALL) {
                int[] domain = domains[side.ordinal()];
                if (slot < domain.length) {
                    currentSide = side;
                    currentTile = domain[slot];
                    return true;
                }
                slot -= domain.length;
            }
            throw new IllegalStateException("option " + option + " is out of range");
        }

        /** Gets the column the current option would place its tile in. */
        int x() {
            return anchorX + currentSide.dx();
        }

        /** Gets the row the current option would place its tile in. */
        int y() {
            return anchorY + currentSide.dy();
        }

        /** Gets the tile the current option would place. */
        int tileIndex() {
            return currentTile;
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
        int[] domain = candidates[x][y];
        if (domain == null) {
            domain = computeDomain(x, y);
            candidates[x][y] = domain;
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
