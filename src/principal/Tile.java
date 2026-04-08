package principal;

import javafx.scene.image.Image;

/**
 * A tile that can be placed on a {@link TileBoard}.
 *
 * <p>The shape of a tile is described by an eight character string, read
 * clockwise around its border starting at the top-left corner:</p>
 *
 * <pre>
 *     0 1 2        0 = top-left      4 = bottom-right
 *     7   3        1 = top-center    5 = bottom-center
 *     6 5 4        2 = top-right     6 = bottom-left
 *                  3 = right-center  7 = left-center
 * </pre>
 *
 * <p>Corner zones are shared by the two sides they belong to, so eight zones
 * are enough to rebuild the three zones of every side. Two tiles may be placed
 * side by side only when the zones of the touching sides are identical.</p>
 *
 * <p>Overlapping sets need one extra zone: the centre of the pattern
 * ({@code midDescription}). It completes the middle row and the middle column
 * that such sets compare when two patterns overlap.</p>
 *
 * <p>A tile is a plain, immutable value: it holds the zones of its border and
 * the image that goes with them, and it knows nothing about files, sampling or
 * pixel juggling. Building the tiles, deriving their mirrors and rotations and
 * loading their graphics is the job of {@link TileSet}.</p>
 *
 * <p>The only piece of mutable state is the {@linkplain #isBottomTile() bottom}
 * flag, which is set while a set is loaded and is only used to seed the
 * generation of overlapping sets.</p>
 */
final class Tile {

    /** Number of zones used to describe the border of a tile. */
    static final int SIDES_LENGTH = 8;
    /** Number of zones of a single side. */
    static final int ZONES_PER_SIDE = 3;

    private final String name;
    private final String sidesDescription;
    private final String midDescription;
    private final boolean forbidsSameNeighbor;
    private final boolean forcesHorizontalMirror;
    private final Image image;

    private final char[] top = new char[ZONES_PER_SIDE];
    private final char[] right = new char[ZONES_PER_SIDE];
    private final char[] bottom = new char[ZONES_PER_SIDE];
    private final char[] left = new char[ZONES_PER_SIDE];
    private final char[] horizontalMid = new char[ZONES_PER_SIDE];
    private final char[] verticalMid = new char[ZONES_PER_SIDE];

    private boolean bottomTile;

    /**
     * Builds a tile from its textual description.
     *
     * @param name                   name of the tile, without extension
     * @param sidesDescription       eight zones read clockwise from the top-left
     * @param midDescription         colour zone at the centre of the pattern, or
     *                               an empty string for non overlapping tiles
     * @param forcesHorizontalMirror whether the horizontal mirror must be added
     *                               even if an equivalent tile already exists
     * @param forbidsSameNeighbor    whether two tiles with the same name must
     *                               never touch each other
     * @param image                  graphic of the tile
     * @throws IllegalArgumentException if the description does not have exactly
     *                                  eight zones
     */
    Tile(String name, String sidesDescription, String midDescription,
         boolean forcesHorizontalMirror, boolean forbidsSameNeighbor, Image image) {
        if (sidesDescription == null || sidesDescription.length() != SIDES_LENGTH) {
            throw new IllegalArgumentException(
                    "sidesDescription of '" + name + "' must have exactly " + SIDES_LENGTH + " zones");
        }

        this.name = name;
        this.sidesDescription = sidesDescription;
        this.midDescription = midDescription == null ? "" : midDescription;
        this.forcesHorizontalMirror = forcesHorizontalMirror;
        this.forbidsSameNeighbor = forbidsSameNeighbor;
        this.image = image;

        // Top: zones 0, 1 and 2.
        top[0] = sidesDescription.charAt(0);
        top[1] = sidesDescription.charAt(1);
        top[2] = sidesDescription.charAt(2);

        // Right: reuses the top-right corner (2) and ends at the bottom-right (4).
        right[0] = sidesDescription.charAt(2);
        right[1] = sidesDescription.charAt(3);
        right[2] = sidesDescription.charAt(4);

        // Bottom: read from right to left, so the bottom-right corner is first.
        bottom[0] = sidesDescription.charAt(6);
        bottom[1] = sidesDescription.charAt(5);
        bottom[2] = sidesDescription.charAt(4);

        // Left: read from bottom to top, sharing both corners with its neighbours.
        left[0] = sidesDescription.charAt(0);
        left[1] = sidesDescription.charAt(7);
        left[2] = sidesDescription.charAt(6);

        char mid = this.midDescription.isEmpty() ? '\0' : this.midDescription.charAt(0);
        horizontalMid[0] = left[1];
        horizontalMid[1] = mid;
        horizontalMid[2] = right[1];
        verticalMid[0] = top[1];
        verticalMid[1] = mid;
        verticalMid[2] = bottom[1];
    }

    /**
     * Creates a copy of this tile with a new border and a new graphic, used to
     * build the rotated and mirrored versions. The derived tile is no longer
     * flagged as "force horizontal mirror".
     *
     * @param newSidesDescription border of the derived tile
     * @param newImage            graphic of the derived tile, already transformed
     * @return the derived tile
     */
    Tile derive(String newSidesDescription, Image newImage) {
        return new Tile(name, newSidesDescription, midDescription,
                false, forbidsSameNeighbor, newImage);
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
     * Gets the eight zone description of the border.
     *
     * @return the border description
     */
    String getSidesDescription() {
        return sidesDescription;
    }

    /**
     * Gets the description of the centre zone.
     *
     * @return the centre zone, or an empty string when the set does not overlap
     */
    String getMidDescription() {
        return midDescription;
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
     * Gets the middle row of the tile, from left to right. Only meaningful for
     * overlapping sets.
     *
     * @return the middle row zones; the returned array must not be modified
     */
    char[] getHorizontalMid() {
        return horizontalMid;
    }

    /**
     * Gets the middle column of the tile, from top to bottom. Only meaningful
     * for overlapping sets.
     *
     * @return the middle column zones; the returned array must not be modified
     */
    char[] getVerticalMid() {
        return verticalMid;
    }
}
