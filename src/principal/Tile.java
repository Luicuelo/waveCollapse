package principal;

import javafx.scene.image.Image;

/**
 * A tile that can be placed on a {@link TileBoard}.
 *
 * <p>The sides are described by twelve characters in the order
 * {@code TTTRRRBBBLLL}. Each side has three independent zones: horizontal sides
 * are read from left to right and vertical sides from top to bottom.
 * Two tiles are compatible when their facing sides match.</p>
 *
 * <p>A tile is a plain, immutable value: it holds the zones of its border and
 * the image that goes with them, and it knows nothing about files, sampling or
 * pixel juggling. Building the tiles, deriving their mirrors and rotations and
 * loading their graphics is the job of {@link TileSet}.</p>
 *
 * <p>The only piece of mutable state is the {@linkplain #isBottomTile() bottom}
 * flag, which marks the ground tiles of a from-bottom set. It is set while the
 * set is loaded, is inherited by the derived variants, and guards both the
 * side index and the bottom-row placement rule.</p>
 */
final class Tile {

    /** Number of zones across all four sides. */
    static final int SIDES_LENGTH = 12;
    /** Number of zones of a single side. */
    static final int ZONES_PER_SIDE = 3;

    private final String name;
    private final String sidesDescription;
    private final boolean forbidsSameNeighbor;
    private final boolean forcesHorizontalMirror;
    private final Image image;

    private final char[] top = new char[ZONES_PER_SIDE];
    private final char[] right = new char[ZONES_PER_SIDE];
    private final char[] bottom = new char[ZONES_PER_SIDE];
    private final char[] left = new char[ZONES_PER_SIDE];

    private boolean bottomTile;

    /**
     * Builds a tile from its textual description.
     *
     * @param name                   name of the tile, without extension
     * @param sidesDescription       twelve zones in the order TTTRRRBBBLLL
     * @param forcesHorizontalMirror whether the horizontal mirror must be added
     *                               even if an equivalent tile already exists
     * @param forbidsSameNeighbor    whether two tiles with the same name must
     *                               never touch each other
     * @param image                  graphic of the tile
     * @throws IllegalArgumentException if the description does not have twelve zones
     */
    Tile(String name, String sidesDescription,
         boolean forcesHorizontalMirror, boolean forbidsSameNeighbor, Image image) {
        if (sidesDescription == null || sidesDescription.length() != SIDES_LENGTH) {
            throw new IllegalArgumentException(
                    "sidesDescription of '" + name + "' must have exactly " + SIDES_LENGTH + " zones");
        }

        this.name = name;
        this.sidesDescription = sidesDescription;
        this.forcesHorizontalMirror = forcesHorizontalMirror;
        this.forbidsSameNeighbor = forbidsSameNeighbor;
        this.image = image;

        sidesDescription.getChars(0, 3, top, 0);
        sidesDescription.getChars(3, 6, right, 0);
        sidesDescription.getChars(6, 9, bottom, 0);
        sidesDescription.getChars(9, 12, left, 0);
    }

    /**
     * Creates a copy of this tile with a new border and a new graphic, used to
     * build the rotated and mirrored versions. The derived tile is no longer
     * flagged as "force horizontal mirror", but it keeps the bottom flag: every
     * variant of a ground tile must stay protected by the side index and by the
     * bottom-row placement rule.
     *
     * @param newSidesDescription border of the derived tile
     * @param newImage            graphic of the derived tile, already transformed
     * @return the derived tile
     */
    Tile derive(String newSidesDescription, Image newImage) {
        Tile derived = new Tile(name, newSidesDescription, false, forbidsSameNeighbor, newImage);
        if (bottomTile) {
            derived.markAsBottomTile();
        }
        return derived;
    }

    /**
     * Marks this tile as belonging to the bottom row of the sample, so it can be
     * used to seed an overlapping generation.
     */
    void markAsBottomTile() {
        this.bottomTile = true;
    }

    /**
     * Tells whether this tile comes from the bottom row of the sample.
     *
     * @return {@code true} if the tile belongs to the bottom row of the sample
     */
    boolean isBottomTile() {
        return bottomTile;
    }

    /**
     * Gets the name of the tile, shared by all its derived versions.
     *
     * @return the original name of the tile
     */
    String getName() {
        return name;
    }

    /**
     * Gets the twelve zones of the sides.
     *
     * @return the border description
     */
    String getSidesDescription() {
        return sidesDescription;
    }

    /**
     * Gets the graphic of the tile.
     *
     * @return the image of the tile
     */
    Image getImage() {
        return image;
    }

    /**
     * Tells whether two tiles with the same name must never be adjacent.
     *
     * @return {@code true} if the tile rejects neighbours with its own name
     */
    boolean forbidsSameNeighbor() {
        return forbidsSameNeighbor;
    }

    /**
     * Tells whether the horizontal mirror must be generated even when an
     * equivalent tile already exists.
     *
     * @return {@code true} if the horizontal mirror is mandatory
     */
    boolean forcesHorizontalMirror() {
        return forcesHorizontalMirror;
    }

    /**
     * Gets the zones of the top side, from left to right.
     *
     * @return the top zones; the returned array must not be modified
     */
    char[] getTop() {
        return top;
    }

    /**
     * Gets the zones of the right side, from top to bottom.
     *
     * @return the right zones; the returned array must not be modified
     */
    char[] getRight() {
        return right;
    }

    /**
     * Gets the zones of the bottom side, from left to right.
     *
     * @return the bottom zones; the returned array must not be modified
     */
    char[] getBottom() {
        return bottom;
    }

    /**
     * Gets the zones of the left side, from top to bottom.
     *
     * @return the left zones; the returned array must not be modified
     */
    char[] getLeft() {
        return left;
    }

    /**
     * Sides of a tile, the compatibility zones they expose, and the step to the
     * neighbour across them.
     *
     * <p>The constants are declared in the order the neighbours are examined on
     * the board. That order decides which placement is tried first and which
     * domains are cleared first, so it is part of what makes a seed reproduce
     * the same board; do not reorder them.</p>
     */
    enum Side {
        TOP(0, -1),
        BOTTOM(0, 1),
        LEFT(-1, 0),
        RIGHT(1, 0);

        static final Side[] ALL = values();

        private final int dx;
        private final int dy;

        Side(int dx, int dy) {
            this.dx = dx;
            this.dy = dy;
        }

        /** How many columns to move to reach the neighbour across this side. */
        int dx() {
            return dx;
        }

        /** How many rows to move to reach the neighbour across this side. */
        int dy() {
            return dy;
        }

        Side opposite() {
            return switch (this) {
                case TOP -> BOTTOM;
                case BOTTOM -> TOP;
                case LEFT -> RIGHT;
                case RIGHT -> LEFT;
            };
        }

        char[] zonesOf(Tile tile) {
            return switch (this) {
                case TOP -> tile.getTop();
                case RIGHT -> tile.getRight();
                case BOTTOM -> tile.getBottom();
                case LEFT -> tile.getLeft();
            };
        }
    }
}
