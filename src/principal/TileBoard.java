package principal;

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
     * Tells whether a position belongs to the board.
     *
     * @param x column
     * @param y row
     * @return {@code true} if the position is inside the board
     */
    boolean contains(int x, int y) {
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
        return contains(x, y) && cells[x][y] == EMPTY;
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
        if (!contains(x, y)) {
            throw new IllegalArgumentException("Position " + x + "," + y + " is outside the board");
        }
        if (set.get(tileIndex) == null) {
            throw new IllegalArgumentException("Unknown tile index: " + tileIndex);
        }
        if (cells[x][y] != EMPTY) {
            throw new IllegalStateException(
                    "Cell " + x + "," + y + " already holds tile " + cells[x][y]);
        }
        cells[x][y] = tileIndex;
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
        if (contains(x, y) && cells[x][y] != EMPTY) {
            cells[x][y] = EMPTY;
            placedCount--;
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
        List<Point> neighbors = new ArrayList<>(4);
        if (y > 0 && cells[x][y - 1] == EMPTY) {
            neighbors.add(new Point(x, y - 1));
        }
        if (y < height - 1 && cells[x][y + 1] == EMPTY) {
            neighbors.add(new Point(x, y + 1));
        }
        if (x > 0 && cells[x - 1][y] == EMPTY) {
            neighbors.add(new Point(x - 1, y));
        }
        if (x < width - 1 && cells[x + 1][y] == EMPTY) {
            neighbors.add(new Point(x + 1, y));
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
        List<Point> neighbors = new ArrayList<>(4);
        if (y > 0 && cells[x][y - 1] != EMPTY) {
            neighbors.add(new Point(x, y - 1));
        }
        if (y < height - 1 && cells[x][y + 1] != EMPTY) {
            neighbors.add(new Point(x, y + 1));
        }
        if (x > 0 && cells[x - 1][y] != EMPTY) {
            neighbors.add(new Point(x - 1, y));
        }
        if (x < width - 1 && cells[x + 1][y] != EMPTY) {
            neighbors.add(new Point(x + 1, y));
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
     * @param x column of the tile used as anchor
     * @param y row of the tile used as anchor
     * @return the number of possible placements
     */
    int countPossiblePlacements(int x, int y) {
        return collectPlacements(x, y).size();
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
        return (y > 0 && cells[x][y - 1] == EMPTY)
                || (y < height - 1 && cells[x][y + 1] == EMPTY)
                || (x > 0 && cells[x - 1][y] == EMPTY)
                || (x < width - 1 && cells[x + 1][y] == EMPTY);
    }

    private List<Placement> collectPlacements(int x, int y) {
        List<Placement> placements = new ArrayList<>();
        if (!contains(x, y) || cells[x][y] == EMPTY) {
            return placements;
        }

        List<Point> targets = getEmptyNeighbors(x, y);
        if (targets.isEmpty()) {
            return placements;
        }

        boolean overlap = set.usesOverlap();
        for (Point target : targets) {
            for (int tileIndex = 0; tileIndex < set.size(); tileIndex++) {
                if (isCompatibleWithNeighbors(target.x(), target.y(), tileIndex, overlap)) {
                    placements.add(new Placement(tileIndex, target.x(), target.y()));
                }
            }
        }
        return placements;
    }

    /**
     * Checks whether a tile could be placed at a position given the tiles that
     * already surround it. Only the neighbours that hold a tile are checked, so
     * the same method works for inner cells and for the border of the board.
     *
     * @param x         column where the tile would go
     * @param y         row where the tile would go
     * @param tileIndex candidate tile
     * @param overlap   whether the set compares the middle row and column too
     * @return {@code true} if no neighbour rejects the candidate
     */
    private boolean isCompatibleWithNeighbors(int x, int y, int tileIndex, boolean overlap) {
        Tile tile = set.get(tileIndex);
        if (tile == null) {
            return false;
        }

        Tile above = tileAt(x, y - 1);
        if (above != null && !matchesAbove(tile, above, overlap)) {
            return false;
        }

        Tile below = tileAt(x, y + 1);
        if (below != null && !matchesBelow(tile, below, overlap)) {
            return false;
        }

        Tile onLeft = tileAt(x - 1, y);
        if (onLeft != null && !matchesLeft(tile, onLeft, overlap)) {
            return false;
        }

        Tile onRight = tileAt(x + 1, y);
        if (onRight != null && !matchesRight(tile, onRight, overlap)) {
            return false;
        }

        return true;
    }

    private Tile tileAt(int x, int y) {
        if (!contains(x, y)) {
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

    private static boolean matchesAbove(Tile tile, Tile above, boolean overlap) {
        if (sameNameConflict(tile, above)) {
            return false;
        }
        if (!overlap) {
            return Arrays.equals(tile.getTop(), above.getBottom());
        }
        return Arrays.equals(tile.getHorizontalMid(), above.getBottom())
                && Arrays.equals(tile.getTop(), above.getHorizontalMid());
    }

    private static boolean matchesBelow(Tile tile, Tile below, boolean overlap) {
        if (sameNameConflict(tile, below)) {
            return false;
        }
        if (!overlap) {
            return Arrays.equals(tile.getBottom(), below.getTop());
        }
        return Arrays.equals(tile.getHorizontalMid(), below.getTop())
                && Arrays.equals(tile.getBottom(), below.getHorizontalMid());
    }

    private static boolean matchesLeft(Tile tile, Tile onLeft, boolean overlap) {
        if (sameNameConflict(tile, onLeft)) {
            return false;
        }
        if (!overlap) {
            return Arrays.equals(tile.getLeft(), onLeft.getRight());
        }
        return Arrays.equals(tile.getVerticalMid(), onLeft.getRight())
                && Arrays.equals(tile.getLeft(), onLeft.getVerticalMid());
    }

    private static boolean matchesRight(Tile tile, Tile onRight, boolean overlap) {
        if (sameNameConflict(tile, onRight)) {
            return false;
        }
        if (!overlap) {
            return Arrays.equals(tile.getRight(), onRight.getLeft());
        }
        return Arrays.equals(tile.getVerticalMid(), onRight.getLeft())
                && Arrays.equals(tile.getRight(), onRight.getVerticalMid());
    }
}
