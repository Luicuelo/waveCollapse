package principal;

import principal.Tile.Side;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Random;

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
     * Buffer the domains are built in, so that rejecting a tile allocates
     * nothing. It is created on first use: its size is the number of tiles, and
     * asking for it here would load the set on the thread that builds the
     * board.
     */
    private int[] scratch;

    /** Lists and neighbours reused when intersecting the options of a domain. */
    private final List<List<Integer>> neighborCandidates =
            new ArrayList<>(Collections.nCopies(Direction.ALL.length, null));
    private final Tile[] neighborTiles = new Tile[Direction.ALL.length];

    private int placedCount;

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
        if (set.get(tileIndex) == null) {
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
        for (Direction direction : Direction.ALL) {
            int neighborX = x + direction.dx();
            int neighborY = y + direction.dy();
            if (inBounds(neighborX, neighborY)) {
                candidates[neighborX][neighborY] = null;
            }
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
        List<Point> neighbors = new ArrayList<>(Direction.ALL.length);
        for (Direction direction : Direction.ALL) {
            int neighborX = x + direction.dx();
            int neighborY = y + direction.dy();
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
        List<Point> neighbors = new ArrayList<>(Direction.ALL.length);
        for (Direction direction : Direction.ALL) {
            int neighborX = x + direction.dx();
            int neighborY = y + direction.dy();
            if (inBounds(neighborX, neighborY) && cells[neighborX][neighborY] != EMPTY) {
                neighbors.add(new Point(neighborX, neighborY));
            }
        }
        return neighbors;
    }

    /**
     * Lists every tile that can be placed in the empty cells around a tile,
     * in random order so that the generation does not look biased.
     *
     * @param x      column of the tile used as anchor
     * @param y      row of the tile used as anchor
     * @param random source of randomness
     * @return the possible placements, empty when the anchor cannot grow
     */
    List<Placement> getPossiblePlacements(int x, int y, Random random) {
        List<Placement> placements = collectPlacements(x, y);
        Collections.shuffle(placements, random);
        return placements;
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
        for (Direction direction : Direction.ALL) {
            count += domainAt(x + direction.dx(), y + direction.dy()).length;
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

        for (int x = 0; x < width; x++) {
            for (int y = 0; y < height; y++) {
                if (cells[x][y] == EMPTY || !hasEmptyNeighbor(x, y)) {
                    continue;
                }

                int options = countPossiblePlacements(x, y);
                if (options < lowest) {
                    lowest = options;
                    best = new Point(x, y);
                    ties = 1;
                } else if (options == lowest && random.nextInt(++ties) == 0) {
                    // Reservoir sampling: keeps every tied candidate equally likely.
                    best = new Point(x, y);
                }
            }
        }
        return best;
    }

    private boolean hasEmptyNeighbor(int x, int y) {
        for (Direction direction : Direction.ALL) {
            int neighborX = x + direction.dx();
            int neighborY = y + direction.dy();
            if (inBounds(neighborX, neighborY) && cells[neighborX][neighborY] == EMPTY) {
                return true;
            }
        }
        return false;
    }

    private List<Placement> collectPlacements(int x, int y) {
        if (!inBounds(x, y) || cells[x][y] == EMPTY) {
            return new ArrayList<>();
        }

        // The domains already know how many placements there will be, so the
        // list is created with its final size and never grows.
        List<Placement> placements = new ArrayList<>(countPossiblePlacements(x, y));
        for (Direction direction : Direction.ALL) {
            int targetX = x + direction.dx();
            int targetY = y + direction.dy();
            for (int tileIndex : domainAt(targetX, targetY)) {
                placements.add(new Placement(tileIndex, targetX, targetY));
            }
        }
        return placements;
    }

    /**
     * Gets the tiles that can go into a cell, computing them the first time they
     * are asked for.
     *
     * <p>A domain is a function of the tiles placed around the cell and of
     * nothing else, so throwing it away is always safe: it is rebuilt from the
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
     * Computes the domain by intersecting the indexed lists of the neighbours.
     * Preserves the set order and applies the rule on neighbour names.
     *
     * @param x column
     * @param y row
     * @return compatible tiles, ordered as in the set
     */
    private int[] computeDomain(int x, int y) {
        if (scratch == null) {
            scratch = new int[set.size()];
        }
        int neighborCount = 0;
        List<Integer> fewest = null;
        for (Direction direction : Direction.ALL) {
            Tile neighbor = tileAt(x + direction.dx(), y + direction.dy());
            if (neighbor == null) {
                continue;
            }
            List<Integer> options = set.compatibleTiles(direction.ownSide(), neighbor);
            if (options.isEmpty()) {
                return NO_TILES;
            }
            neighborCandidates.set(neighborCount, options);
            neighborTiles[neighborCount++] = neighbor;
            if (fewest == null || options.size() < fewest.size()) {
                fewest = options;
            }
        }

        int compatible = 0;
        if (fewest == null) {
            for (int i = 0; i < scratch.length; i++) {
                scratch[compatible++] = i;
            }
        } else {
            candidates:
            for (int tileIndex : fewest) {
                Tile tile = set.get(tileIndex);
                for (int i = 0; i < neighborCount; i++) {
                    List<Integer> options = neighborCandidates.get(i);
                    if ((options != fewest && Collections.binarySearch(options, tileIndex) < 0)
                            || sameNameConflict(tile, neighborTiles[i])) {
                        continue candidates;
                    }
                }
                scratch[compatible++] = tileIndex;
            }
        }
        return compatible == 0 ? NO_TILES : Arrays.copyOf(scratch, compatible);
    }

    private Tile tileAt(int x, int y) {
        if (!inBounds(x, y)) {
            return null;
        }
        int tileIndex = cells[x][y];
        return tileIndex == EMPTY ? null : set.get(tileIndex);
    }

    /**
     * Two tiles with the same name may not touch when the candidate forbids it.
     */
    private static boolean sameNameConflict(Tile tile, Tile neighbor) {
        return tile.forbidsSameNeighbor() && neighbor.getName().equals(tile.getName());
    }

    /**
     * A direction towards a neighbour, together with the side of the tile that
     * faces it.
     *
     * <p>The constants are declared in the order the neighbours are examined. That
     * order decides which placement is tried first and which tiles are cleared
     * first, so it is part of what makes a seed reproduce the same board.</p>
     */
    private enum Direction {

        /** The neighbour is above: the top side of the tile meets its bottom. */
        UP(0, -1, Side.TOP),

        /** The neighbour is below: the bottom side of the tile meets its top. */
        DOWN(0, 1, Side.BOTTOM),

        /** The neighbour is on the left: the left side meets its right. */
        LEFT(-1, 0, Side.LEFT),

        /** The neighbour is on the right: the right side meets its left. */
        RIGHT(1, 0, Side.RIGHT);

        /**
         * The four directions, ready to iterate. It is kept because
         * {@code values()} returns a fresh array on every call.
         */
        private static final Direction[] ALL = values();

        private final int dx;
        private final int dy;
        private final Side ownSide;

        Direction(int dx, int dy, Side ownSide) {
            this.dx = dx;
            this.dy = dy;
            this.ownSide = ownSide;
        }

        /**
         * Gets how many columns to move to reach the neighbour.
         *
         * @return the column step
         */
        private int dx() {
            return dx;
        }

        /**
         * Gets how many rows to move to reach the neighbour.
         *
         * @return the row step
         */
        private int dy() {
            return dy;
        }

        /**
         * Gets the side of the tile that faces the neighbour.
         *
         * @return the own side
         */
        private Side ownSide() {
            return ownSide;
        }
    }
}
