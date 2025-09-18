package principal;

import javafx.scene.image.Image;
import javafx.scene.image.PixelReader;
import javafx.scene.image.PixelWriter;
import javafx.scene.image.WritableImage;
import javafx.scene.paint.Color;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Random;
import java.util.Set;

/**
 * Manages circuit tile definitions and operations for the Wave Function
 * Collapse algorithm.
 * <p>
 * This class handles the loading, generation, and management of circuit tiles
 * used in the
 * Wave Function Collapse algorithm. Each tile represents a circuit component
 * with specific
 * connection properties on its sides. Tiles can be original or derived versions
 * (rotated/mirrored)
 * of base tiles.
 * </p>
 * <p>
 * The tiles are defined by their side connections, with each side having 3
 * zones that determine
 * compatibility with adjacent tiles. The sides are ordered clockwise: top,
 * right, bottom, left.
 * </p>
 */
public class TitlesManager {

    protected enum TitleSet {

        CIRCUIT("circuit", 14,SetType.FROMTITLES),
        CASTLE("castle", 7,SetType.FROMTITLES),
        KNOTS("knots", 10,SetType.FROMTITLES),
        SIMPLE("simple", 3,SetType.FROMTITLES),
        FLOORPLAN("floorPlan", 9,SetType.FROMTITLES),
        ROOMS("rooms", 3,SetType.FROMTITLES),
        CIRCLES("circles", 32,SetType.FROMTITLES);

        protected static enum SetType {
            FROMTITLES,
            FROMSAMPLE
        }

        private final String titleSetName;
        private final int titleSetSize;
        private final SetType setType;

        TitleSet(String name, int size, SetType setType) {
            this.titleSetName = name;
            this.titleSetSize = size;
            this.setType=setType;
        }

        public String getName() {
            return titleSetName;
        }

        public int getSize() {
            return titleSetSize;
        }

        public SetType getSetType(){
            return this.setType;
        }
    }

    private static final List<Title> Titles = new ArrayList<>();

    /**
     * Loads the base tiles and generates their derived versions.
     * <p>
     * Each side of a tile has 3 zones, right, center, left.
     * The zones are defined by a color: G (Green), B (Blue), W (White), D (Dark).
     * If all the colors of a side match with an adjacent tile, they can be painted
     * together.
     * </p>
     * <p>
     * To define a tile the order of sides are top, right, bottom, left (clockwise).
     * The last zone of top side is the same as the first zone of the right side, so
     * to define the tile,
     * we need 3 zones of top, 2 zones of right, 2 zones of bottom, 1 zone of left,
     * total 8 zones.
     * </p>
     */
    public static void loadTitles(TitleSet set) {
        Titles.clear();
        switch (set) {
            case CIRCUIT:
                Titles.add(new Title(set, "bridge", "GBGWGBGW"));
                Titles.add(new Title(set, "component", "DDDDDDDD"));
                Titles.add(new Title(set, "connection", "GBGGDDDG"));
                Titles.add(new Title(set, "corner", "GGGGGGDG"));
                Titles.add(new Title(set, "dskew", "GBGBGBGB", true, false));
                Titles.add(new Title(set, "ecomponent", "GGGGDDDG"));
                Titles.add(new Title(set, "skew", "GBGBGGGG"));
                Titles.add(new Title(set, "substrate", "GGGGGGGG"));
                Titles.add(new Title(set, "t", "GGGBGBGB"));
                Titles.add(new Title(set, "track", "GBGGGBGG"));
                Titles.add(new Title(set, "transition", "GWGGGBGG"));
                Titles.add(new Title(set, "turn", "GBGBGGGG"));
                Titles.add(new Title(set, "viad", "GGGBGGGB"));
                Titles.add(new Title(set, "vias", "GBGGGGGG"));
                Titles.add(new Title(set, "wire", "GGGWGGGW"));
                break;
            case CASTLE:
                Titles.add(new Title(set, "bridge", "GBGYGBGY"));
                Titles.add(new Title(set, "ground", "GGGGGGGG"));
                Titles.add(new Title(set, "river", "GBGGGBGG"));
                Titles.add(new Title(set, "riverturn", "GBGBGGGG"));
                Titles.add(new Title(set, "road", "GYGGGYGG"));
                Titles.add(new Title(set, "roadturn", "GYGYGGGG"));
                Titles.add(new Title(set, "t", "GGGYGYGY"));
                Titles.add(new Title(set, "tower", "GWGWGGGG", true));
                Titles.add(new Title(set, "wall", "GWGGGWGG"));
                Titles.add(new Title(set, "wallriver", "GWGBGWGB"));
                Titles.add(new Title(set, "wallroad", "GWGYGWGY"));
                break;
            case KNOTS:
                Titles.add(new Title(set, "corner", "WCWCWWWW"));
                Titles.add(new Title(set, "cross", "WCWCWCWC"));
                Titles.add(new Title(set, "empty", "WWWWWWWW"));
                Titles.add(new Title(set, "line", "WWWCWWWC"));
                Titles.add(new Title(set, "t", "WWWCWCWC"));
                break;
            case SIMPLE:
                Titles.add(new Title(set, "corner", "WCWCWWWW"));
                Titles.add(new Title(set, "cross", "WCWCWCWC"));
                Titles.add(new Title(set, "blank", "WWWWWWWW"));
                Titles.add(new Title(set, "line", "WWWCWWWC"));
                Titles.add(new Title(set, "t", "WWWCWCWC"));
                break;
            case FLOORPLAN:
                Titles.add(new Title(set, "div", "GGGLGGGL"));
                Titles.add(new Title(set, "divt", "GGGLGLGL"));
                Titles.add(new Title(set, "divturn", "GLGLGGGG"));
                Titles.add(new Title(set, "door", "GGGLGGGL"));
                Titles.add(new Title(set, "empty", "WWWWWWWW"));
                Titles.add(new Title(set, "floor", "GGGGGGGG"));
                Titles.add(new Title(set, "glass", "GGGMWWWM"));
                Titles.add(new Title(set, "halfglass", "GGGMWWWD"));
                Titles.add(new Title(set, "in", "GGGGGDWD"));
                Titles.add(new Title(set, "out", "WDGDWWWW"));
                Titles.add(new Title(set, "stairs", "GGGLGSGL"));
                Titles.add(new Title(set, "table", "GGGGGGGG", true));
                Titles.add(new Title(set, "vent", "WWWWWWWW", true));
                Titles.add(new Title(set, "w", "GGGDWWWD", true));
                Titles.add(new Title(set, "wall", "GGGDWWWD"));
                Titles.add(new Title(set, "walldiv", "GLGDWWWD"));
                Titles.add(new Title(set, "window", "GGGDWWWD", true));
                break;
            case ROOMS:
                Titles.add(new Title(set, "bend", "WBBBWWWW"));
                Titles.add(new Title(set, "corner", "BBWBBBBB"));
                Titles.add(new Title(set, "corridor", "BWBBBWBB"));
                Titles.add(new Title(set, "door", "WWWBBWBB"));
                Titles.add(new Title(set, "empty", "WWWWWWWW"));
                Titles.add(new Title(set, "side", "BBBBWWWB"));
                Titles.add(new Title(set, "t", "BBBWBWBW"));
                Titles.add(new Title(set, "turn", "BWBWBBBB"));
                Titles.add(new Title(set, "wall", "BBBBBBBB"));
                break;
            case CIRCLES:
                Titles.add(new Title(set, "b", "WBWBWBWB"));
                Titles.add(new Title(set, "w", "BWBWBWBW"));
                Titles.add(new Title(set, "b_half", "WBWWWWWW"));
                Titles.add(new Title(set, "w_half", "BWBBBBBB"));
                Titles.add(new Title(set, "b_i", "WBWWWBWW"));
                Titles.add(new Title(set, "w_i", "BWBBBWBB"));
                Titles.add(new Title(set, "b_quarter", "WBWBWWWW"));
                Titles.add(new Title(set, "w_quarter", "BWBWBBBB"));
                break;
            default:
                break;
        }

        while (generateDerivedTiles()) {
        }
    }

    /**
     * Gets the total number of tiles (base and derived).
     *
     * @return the number of tiles
     */
    public static int getTitlesCount() {
        return Titles.size();
    }

    /**
     * Gets the name of a tile at the specified index.
     *
     * @param index the index of the tile
     * @return the name of the tile
     */
    public static String getTitleName(int index) {
        if (index >= 0 && index < Titles.size()) {
            return Titles.get(index).originalName;
        }
        return "";
    }

    public static int getTitleSize(int index) {
        if (index >= 0 && index < Titles.size()) {
            return Titles.get(index).getSet().getSize();
        }
        return -1;
    }

    /**
     * Gets information about a tile at the specified index.
     *
     * @param index the index of the tile
     * @return information about the tile
     */
    public static String getTitleInfo(int index) {
        if (index >= 0 && index < Titles.size()) {
            Title t = Titles.get(index);
            return t.originalName + " (" + t.sidesDescription + ")";
        }
        return "Unknown tile";
    }

    /**
     * Generates derived versions of the base tiles through rotation and mirroring.
     * <p>
     * This method creates additional tiles by applying transformations to the base
     * tiles:
     * horizontal mirroring, vertical mirroring, and rotations (90, 180, 270
     * degrees).
     * </p>
     */
    private static boolean generateDerivedTiles() {
        // Only process base tiles (non-derived)
        List<Title> baseTitles = new ArrayList<>();
        for (Title Title : Titles) {
            baseTitles.add(Title);
        }

        boolean newAdded = false;
        // Process each base tile
        for (Title original : baseTitles) {
            newAdded |= addIfNotExists(original, DerivedType.horizontalMirror,
                    createHorizontallyMirroredDescription(original));
            newAdded |= addIfNotExists(original, DerivedType.verticalMirror,
                    createVerticallyMirroredDescription(original));
            newAdded |= addIfNotExists(original, DerivedType.clockwise90, createRotatedDescription(original, 90));
            newAdded |= addIfNotExists(original, DerivedType.clockwise180, createRotatedDescription(original, 180));
            newAdded |= addIfNotExists(original, DerivedType.getClockwise270, createRotatedDescription(original, 270));
        }

        return newAdded;
    }

    // represents the board of Titles to be painted

    /**
     * Checks if a transformation type is horizontal mirror.
     *
     * @param type the transformation type to check
     * @return true if the type is horizontalMirror, false otherwise
     */
    private static boolean derivedTypeIsHorizontalMirror(DerivedType type) {
        return type == DerivedType.horizontalMirror;

    }

    /**
     * Adds a derived tile if a tile with the same description doesn't already
     * exist.
     *
     * @param original    the original tile
     * @param type        the transformation type
     * @param description the description of the derived tile
     */
    private static boolean addIfNotExists(Title original, DerivedType type, String description) {
        boolean descriptionExists = isDescriptionExists(description, original.originalName);
        if (!descriptionExists
                || (original.forceHorizontalMirror && derivedTypeIsHorizontalMirror(type))) {
            int insertIndex = findLastIndexWithName(original.originalName) + 1;
            Title newTitle = original.titleClone(description, type);
            Titles.add(insertIndex, newTitle);
            // System.out.println("Origina "+original.originalName+" tipo" +
            // original.derivedType + " description" + original.sidesDescription);
            // System.out.println("Se añade:"+newTitle.originalName+" tipo" +
            // newTitle.derivedType + " description" + newTitle.sidesDescription);
            return !descriptionExists;
        }
        return false;
    }

    /**
     * Finds the last index of a tile with the specified name.
     *
     * @param name the name to search for
     * @return the last index of a tile with the specified name, or -1 if not found
     */
    private static int findLastIndexWithName(String name) {
        for (int i = Titles.size() - 1; i >= 0; i--) {
            if (Titles.get(i).originalName.equals(name)) {
                return i;
            }
        }
        return -1;
    }

    /**
     * Checks if a tile with the specified description already exists for the given
     * original name.
     *
     * @param description  the description to check
     * @param originalName the original name of the tile
     * @return true if a tile with the same description exists, false otherwise
     */
    private static boolean isDescriptionExists(String description, String originalName) {
        for (Title Title : Titles) {
            // Only check tiles with the same original name
            if (Title.originalName.equals(originalName) &&
                    Title.sidesDescription.equals(description)) {
                return true;
            }
        }
        return false;
    }

    /**
     * Creates a horizontally mirrored description of a tile.
     * <p>
     * For horizontal mirror: swap right and left sides, reverse top and bottom
     * sides.
     * </p>
     *
     * @param Title the original tile
     * @return the description of the horizontally mirrored tile
     */
    private static String createHorizontallyMirroredDescription(Title Title) {
        // For horizontal mirror: swap right and left sides
        char[] desc = Title.sidesDescription.toCharArray();
        char[] mirrored = new char[8];

        mirrored[0] = desc[2]; // top-left = original top-right
        mirrored[7] = desc[3]; // left-center = original right-center
        mirrored[6] = desc[4]; // bottom-right = original bottom-left (also right-bottom)

        mirrored[2] = desc[0]; // top-right = original top-left (also left-top)
        mirrored[3] = desc[7]; // right-center = original left-center
        mirrored[4] = desc[6]; // right-bottom = original left-top (also bottom-left)

        mirrored[1] = desc[1]; // top-center = original top-center
        mirrored[5] = desc[5]; // bottom-center = original bottom-center

        return new String(mirrored);
    }

    /**
     * Creates a vertically mirrored description of a tile.
     * <p>
     * For vertical mirror: swap top and bottom sides, reverse left and right sides.
     * </p>
     *
     * @param Title the original tile
     * @return the description of the vertically mirrored tile
     */
    private static String createVerticallyMirroredDescription(Title Title) {
        // For vertical mirror: swap top and bottom sides, reverse left and right sides
        char[] desc = Title.sidesDescription.toCharArray();
        char[] mirrored = new char[8];

        // In vertical flip:

        mirrored[0] = desc[6]; // top-left
        mirrored[1] = desc[5]; // top-center
        mirrored[2] = desc[4]; // top-right

        mirrored[6] = desc[0]; // bottom-right
        mirrored[5] = desc[1]; // bottom-center
        mirrored[4] = desc[2]; // right-bottom

        mirrored[3] = desc[3]; // right-center = original right-center
        mirrored[7] = desc[7]; // left-center = original left-center

        return new String(mirrored);
    }

    /**
     * Creates a rotated description of a tile by the specified degrees.
     * <p>
     * For each 90-degree rotation, we move the last 2 characters to the front.
     * This works because the last 2 chars represent the left side (indices 6,7),
     * and moving them to the front makes them the new top side, with the rest
     * shifting accordingly.
     * </p>
     *
     * @param Title   the original tile
     * @param degrees the degrees to rotate (must be multiple of 90)
     * @return the description of the rotated tile
     */
    private static String createRotatedDescription(Title Title, int degrees) {
        String desc = Title.sidesDescription;

        // For each 90-degree rotation, we move the last 2 characters to the front
        // This works because:
        // - The last 2 chars represent the left side (indices 6,7)
        // - Moving them to the front makes them the new top side
        // - The rest shift accordingly

        int numRotations = degrees / 90;
        String rotatedDesc = desc;

        for (int i = 0; i < numRotations; i++) {
            // Take the last 2 characters and put them at the beginning
            rotatedDesc = rotatedDesc.substring(6) + rotatedDesc.substring(0, 6);
        }

        return rotatedDesc;
    }

    /**
     * Creates a 90-degree clockwise rotated version of an image.
     *
     * @param image1 the original image
     * @return a new image rotated 90 degrees clockwise
     */
    private static Image createRotatedImage(Image image1) {
        int w = (int) image1.getWidth();
        int h = (int) image1.getHeight();

        PixelReader reader = image1.getPixelReader();
        WritableImage image2 = new WritableImage(h, w); // rotated dimension
        PixelWriter writer = image2.getPixelWriter();

        for (int y = 0; y < h; y++) {
            for (int x = 0; x < w; x++) {
                Color color = reader.getColor(x, y);
                writer.setColor(h - y - 1, x, color); // 90° clockwise
            }
        }

        return image2;
    }

    /**
     * Creates a horizontally mirrored version of an image.
     *
     * @param image1 the original image
     * @return a new image mirrored horizontally
     */
    private static Image createMirroredHorizontally(Image image1) {
        int w = (int) image1.getWidth();
        int h = (int) image1.getHeight();

        PixelReader reader = image1.getPixelReader();
        WritableImage mirroredImage = new WritableImage(w, h);
        PixelWriter writer = mirroredImage.getPixelWriter();

        for (int y = 0; y < h; y++) {
            for (int x = 0; x < w; x++) {
                Color color = reader.getColor(x, y);
                // Mirror horizontally by flipping the x coordinate
                writer.setColor(w - x - 1, y, color);
            }
        }

        return mirroredImage;
    }

    /**
     * Creates a vertically mirrored version of an image.
     *
     * @param image1 the original image
     * @return a new image mirrored vertically
     */
    private static Image createMirroredVertically(Image image1) {
        int w = (int) image1.getWidth();
        int h = (int) image1.getHeight();

        PixelReader reader = image1.getPixelReader();
        WritableImage mirroredImage = new WritableImage(w, h);
        PixelWriter writer = mirroredImage.getPixelWriter();

        for (int y = 0; y < h; y++) {
            for (int x = 0; x < w; x++) {
                Color color = reader.getColor(x, y);
                // Mirror vertically by flipping the y coordinate
                writer.setColor(x, h - y - 1, color);
            }
        }

        return mirroredImage;
    }

    /**
     * Gets the tile at the specified index
     *
     * @param index the index of the tile to retrieve
     * @return the tile at the specified index
     */
    public static Title getTitle(int index) {
        if (index >= 0 && index < Titles.size()) {
            return Titles.get(index);
        }
        return null;
    }

    /**
     * Enum representing the transformation type applied to derive a tile from its
     * original form.
     * These transformations include no transformation, horizontal mirroring,
     * vertical mirroring,
     * and rotations in 90-degree increments.
     */
    protected enum DerivedType {
        /**
         * No transformation applied
         */
        none,
        /**
         * Horizontally mirrored version
         */
        horizontalMirror,
        /**
         * Vertically mirrored version
         */
        verticalMirror,
        /**
         * Rotated 90 degrees clockwise
         */
        clockwise90,
        /**
         * Rotated 180 degrees clockwise
         */
        clockwise180,
        /**
         * Rotated 270 degrees clockwise
         */
        getClockwise270
    }

    /**
     * Represents the board of tiles to be painted.
     * <p>
     * This class manages a 2D grid of tile indices that represent the placement of
     * tiles
     * on the board for visualization.
     * </p>
     */
    protected static class TitleBoard {

        /**
         * Matrix to store the board, each cell contains a tile index.
         * -1 indicates an empty cell.
         */
        private static int[][] board;
        /**
         * The number of tiles currently placed on the board
         */
        private static int numberOfTitles = 0;

        /**
         * Gets the number of tiles currently placed on the board.
         *
         * @return the number of tiles on the board
         */
        public static int getNumberOfTitles() {
            return numberOfTitles;
        }

        /**
         * Initializes the board with the specified dimensions.
         *
         * @param width  the width of the board in tiles
         * @param height the height of the board in tiles
         */
        public static void initializeBoard(int width, int height) {
            board = new int[width][height];
            numberOfTitles = 0;
            for (int i = 0; i < width; i++)
                for (int j = 0; j < height; j++)
                    board[i][j] = -1;
        }

        /**
         * Gets the width of the board.
         *
         * @return the width of the board in tiles
         */
        public static int getBoardWidth() {
            return board.length;
        }

        /**
         * Gets the height of the board.
         *
         * @return the height of the board in tiles
         */
        public static int getBoardHeight() {
            return board[0].length;
        }

        /**
         * Gets the tile index at the specified position.
         *
         * @param x the x-coordinate
         * @param y the y-coordinate
         * @return the tile index at the specified position, or -1 if empty
         */
        public static int getTitleIndex(int x, int y) {
            return board[x][y];
        }

        /**
         * Gets the position of a tile by its number on the board.
         *
         * @param number the tile number (1-based)
         * @return the position of the tile, or null if not found
         */
        public static PointInt getTitlePosByNumber(int number) {
            if (number > numberOfTitles)
                return null;
            int count = 0;
            for (int j = 0; j < getBoardHeight(); j++) {
                for (int i = 0; i < getBoardWidth(); i++) {
                    if (board[i][j] != -1)
                        count++;
                    if (count == number)
                        return new PointInt(i, j);
                }
            }
            return null;
        }

        /**
         * Sets the tile index at the specified position on the board.
         *
         * @param x     the x-coordinate
         * @param y     the y-coordinate
         * @param index the tile index to place
         * @throws IllegalArgumentException if the index is invalid
         */
        public static void setTitleAtPos(int x, int y, int index) {
            // check index is valid,
            if (index < 0 || index >= Titles.size()) {
                throw new IllegalArgumentException("Invalid Title index: " + index);
            }
            if (board[x][y] == -1) {
                numberOfTitles++;
            }
            board[x][y] = index;
        }

        /**
         * Removes a tile from the specified position on the board.
         *
         * @param x the x-coordinate
         * @param y the y-coordinate
         */
        public static void delTitleAtPos(int x, int y) {
            if (board[x][y] != -1) {
                numberOfTitles--;
            }
            board[x][y] = -1;
        }

        /**
         * Compares two character arrays for equality.
         *
         * @param array1 the first array to compare
         * @param array2 the second array to compare
         * @return true if the arrays are equal, false otherwise
         */
        private static boolean compareArrays(char[] array1, char[] array2) {
            if (array1.length != array2.length) {
                return false;
            }
            for (int i = 0; i < array1.length; i++) {
                if (array1[i] != array2[i])
                    return false;
            }
            return true;
        }

        /**
         * gets the position with less possible Title
         *
         * @return the position with less possible Title
         * @throws IllegalArgumentException if the index is invalid
         */

        public static PointInt getPosWithLessPossibleTitle() {
            PointInt pos = null;
            int min = Integer.MAX_VALUE;

            for (int i = 0; i < getBoardWidth(); i++) {
                for (int j = 0; j < getBoardHeight(); j++) {
                    if (TitleBoard.getTitleIndex(i, j) == -1) {
                        continue;
                    }

                    // Check if this position has any empty adjacent spaces
                    boolean hasEmptyAdjacent = i > 0 && getTitleIndex(i - 1, j) == -1;
                    if (i < getBoardWidth() - 1 && getTitleIndex(i + 1, j) == -1)
                        hasEmptyAdjacent = true;
                    if (j > 0 && getTitleIndex(i, j - 1) == -1)
                        hasEmptyAdjacent = true;
                    if (j < getBoardHeight() - 1 && getTitleIndex(i, j + 1) == -1)
                        hasEmptyAdjacent = true;

                    if (!hasEmptyAdjacent)
                        continue;

                    int numOfPossibleTitles = getPossibleTitles(i, j).size();
                    if (numOfPossibleTitles < min) {
                        min = numOfPossibleTitles;
                        pos = new PointInt(i, j);
                    }
                }
            }
            return pos;
        }

        /**
         * Finds an impossible position near the given cell by checking its neighbors.
         * This is more efficient than searching the entire board when we already know
         * a problematic area.
         *
         * @param x the x-coordinate of the reference cell
         * @param y the y-coordinate of the reference cell
         * @return the position of an impossible neighbor cell, or null if none found
         */
        public static PointInt findImpossiblePositionNear(int x, int y) {
            // Check all adjacent positions
            int[][] neighbors = { { x - 1, y }, { x + 1, y }, { x, y - 1 }, { x, y + 1 } };

            for (int[] neighbor : neighbors) {
                int nx = neighbor[0];
                int ny = neighbor[1];

                // Check bounds
                if (nx >= 0 && nx < getBoardWidth() && ny >= 0 && ny < getBoardHeight()) {
                    // Check only empty cells
                    if (getTitleIndex(nx, ny) == -1) {
                        // Check if this position has any adjacent filled tiles
                        boolean hasAdjacent = (nx > 0 && getTitleIndex(nx - 1, ny) != -1) ||
                                (nx < getBoardWidth() - 1 && getTitleIndex(nx + 1, ny) != -1) ||
                                (ny > 0 && getTitleIndex(nx, ny - 1) != -1) ||
                                (ny < getBoardHeight() - 1 && getTitleIndex(nx, ny + 1) != -1);

                        // If this empty cell has adjacent tiles, check if any tiles can be placed here
                        if (hasAdjacent && getPossibleTilesForEmptyPosition(nx, ny).isEmpty()) {
                            return new PointInt(nx, ny);
                        }
                    }
                }
            }
            return null;
        }

        /**
         * Gets possible Titles that can be placed at the specified position.
         *
         * @param x the x-coordinate
         * @param y the y-coordinate
         * @return list of possible Titles with their directions
         */
        public static List<TitleAndDirection> getPossibleTitles(int x, int y) {
            return getPossibleTitles(x, y, null);
        }

        /**
         * Gets possible Titles that can be placed at the specified position.
         *
         * @param x                   the x-coordinate
         * @param y                   the y-coordinate
         * @param forceEmptyDirection if not null, treat this direction as empty
         * @return list of possible Titles with their directions
         */
        public static List<TitleAndDirection> getPossibleTitles(int x, int y, Direction forceEmptyDirection) {
            List<TitleAndDirection> possibleTitles = new ArrayList<>();
            int TitleIndex = TitleBoard.getTitleIndex(x, y);

            // If the position is empty, return an empty list
            if (TitleIndex == -1) {
                return possibleTitles;
            }

            // Make sure the index is valid before accessing the tile
            if (TitleIndex < 0 || TitleIndex >= Titles.size()) {
                return possibleTitles;
            }

            Title actualTitle = Titles.get(TitleIndex);

            // check Top
            if (y > 0) {
                int topTitleIndex = (forceEmptyDirection == Direction.TOP) ? -1 : TitleBoard.getTitleIndex(x, y - 1);
                if (topTitleIndex == -1) {
                    for (int i = 0; i < Titles.size(); i++) {
                        Title candidate = Titles.get(i);
                        if (compareArrays(actualTitle.top, candidate.bottom)
                                && isCompatibleWithNeighbors(x, y - 1, i)) {
                            possibleTitles.add(new TitleAndDirection(i, Direction.TOP));
                        }
                    }
                }
            }
            // check Bottom
            if (y < TitleBoard.getBoardHeight() - 1) {
                int bottomTitleIndex = (forceEmptyDirection == Direction.BOTTOM) ? -1
                        : TitleBoard.getTitleIndex(x, y + 1);
                if (bottomTitleIndex == -1) {
                    for (int i = 0; i < Titles.size(); i++) {
                        Title candidate = Titles.get(i);
                        if (compareArrays(actualTitle.bottom, candidate.top)
                                && isCompatibleWithNeighbors(x, y + 1, i)) {
                            possibleTitles.add(new TitleAndDirection(i, Direction.BOTTOM));
                        }
                    }
                }
            }
            // check Right
            if (x < TitleBoard.getBoardWidth() - 1) {
                int rightTitleIndex = (forceEmptyDirection == Direction.RIGHT) ? -1
                        : TitleBoard.getTitleIndex(x + 1, y);
                if (rightTitleIndex == -1) {
                    for (int i = 0; i < Titles.size(); i++) {
                        Title candidate = Titles.get(i);
                        if (compareArrays(actualTitle.right, candidate.left)
                                && isCompatibleWithNeighbors(x + 1, y, i)) {
                            possibleTitles.add(new TitleAndDirection(i, Direction.RIGHT));
                        }
                    }
                }
            }
            // check Left
            if (x > 0) {
                int leftTitleIndex = (forceEmptyDirection == Direction.LEFT) ? -1 : TitleBoard.getTitleIndex(x - 1, y);
                if (leftTitleIndex == -1) {
                    for (int i = 0; i < Titles.size(); i++) {
                        Title candidate = Titles.get(i);
                        if (compareArrays(actualTitle.left, candidate.right)
                                && isCompatibleWithNeighbors(x - 1, y, i)) {
                            possibleTitles.add(new TitleAndDirection(i, Direction.LEFT));
                        }
                    }
                }
            }
            Collections.shuffle(possibleTitles);
            return possibleTitles;
        }

        private static boolean isCompatibleWithNeighbors(int x, int y, int tileIndex) {
            Title tile = Titles.get(tileIndex);

            // Check compatibility with all adjacent tiles that exist
            // Top
            if (y > 0) {
                int topTileIndex = getTitleIndex(x, y - 1);
                if (topTileIndex != -1) {
                    Title topTile = Titles.get(topTileIndex);
                    if (topTile.originalName.equals(tile.originalName) && tile.noSameNeighbor)
                        return false;
                    if (!compareArrays(tile.top, topTile.bottom))
                        return false;
                }
            }
            // Bottom
            if (y < getBoardHeight() - 1) {
                int bottomTileIndex = getTitleIndex(x, y + 1);
                if (bottomTileIndex != -1) {
                    Title bottomTile = Titles.get(bottomTileIndex);
                    if (bottomTile.originalName.equals(tile.originalName) && tile.noSameNeighbor)
                        return false;
                    if (!compareArrays(tile.bottom, bottomTile.top))
                        return false;
                }
            }
            // Left
            if (x > 0) {
                int leftTileIndex = getTitleIndex(x - 1, y);
                if (leftTileIndex != -1) {
                    Title leftTile = Titles.get(leftTileIndex);
                    if (leftTile.originalName.equals(tile.originalName) && tile.noSameNeighbor)
                        return false;
                    if (!compareArrays(tile.left, leftTile.right))
                        return false;
                }
            }
            // Right
            if (x < getBoardWidth() - 1) {
                int rightTileIndex = getTitleIndex(x + 1, y);
                if (rightTileIndex != -1) {
                    Title rightTile = Titles.get(rightTileIndex);
                    if (rightTile.originalName.equals(tile.originalName) && tile.noSameNeighbor)
                        return false;
                    return compareArrays(tile.right, rightTile.left);
                }
            }
            // Tiles at edges are valid if they don't conflict with existing neighbors
            return true;
        }

        /**
         * Gets a random tile to remove when the algorithm is stuck.
         * Finds a position with no possible tiles and removes a random adjacent tile.
         *
         * @param x the x-coordinate of the impossible position
         * @param y the y-coordinate of the impossible position
         * @return the position of a random adjacent tile to remove
         */
        public static PointInt getRandomTileToRemove(int x, int y) {
            // Get all adjacent positions
            List<PointInt> adjacentPositions = new ArrayList<>();

            // Check top neighbor
            if (y > 0 && getTitleIndex(x, y - 1) != -1) {
                adjacentPositions.add(new PointInt(x, y - 1));
            }

            // Check bottom neighbor
            if (y < getBoardHeight() - 1 && getTitleIndex(x, y + 1) != -1) {
                adjacentPositions.add(new PointInt(x, y + 1));
            }

            // Check left neighbor
            if (x > 0 && getTitleIndex(x - 1, y) != -1) {
                adjacentPositions.add(new PointInt(x - 1, y));
            }

            // Check right neighbor
            if (x < getBoardWidth() - 1 && getTitleIndex(x + 1, y) != -1) {
                adjacentPositions.add(new PointInt(x + 1, y));
            }

            // If we have adjacent tiles, randomly select one to remove
            if (!adjacentPositions.isEmpty()) {
                Random random = new Random();
                return adjacentPositions.get(random.nextInt(adjacentPositions.size()));
            }

            // Fallback: return the original position if no adjacent tiles found
            return new PointInt(x, y);
        }

        /**
         * Gets the middle point of the board.
         *
         * @return the middle point coordinates
         */
        public static PointInt getMiddlePoint() {
            return new PointInt(getBoardWidth() / 2, getBoardHeight() / 2);
        }

        /**
         * Gets a random tile index.
         *
         * @return a random tile index
         */
        public static int getRandomTitleIndex() {
            return (int) (Math.random() * Titles.size());
        }

        /**
         * Gets possible tiles that can be placed at an empty position.
         *
         * @param x the x-coordinate of the empty position
         * @param y the y-coordinate of the empty position
         * @return list of possible tiles that can be placed at this position
         */
        public static List<Integer> getPossibleTilesForEmptyPosition(int x, int y) {
            List<Integer> possibleTiles = new ArrayList<>();

            // Check that the position is actually empty
            if (getTitleIndex(x, y) != -1) {
                return possibleTiles; // Position is not empty
            }

            // Check all possible tiles that could be placed at this empty position
            for (int i = 0; i < Titles.size(); i++) {
                if (isCompatibleWithNeighbors(x, y, i)) {
                    possibleTiles.add(i);
                }
            }

            return possibleTiles;
        }

        /**
         * Represents the four possible directions in the grid: top, bottom, left, and
         * right.
         * Used for specifying adjacency relationships between tiles.
         */
        public enum Direction {
            /**
             * Top direction
             */
            TOP,
            /**
             * Bottom direction
             */
            BOTTOM,
            /**
             * Left direction
             */
            LEFT,
            /**
             * Right direction
             */
            RIGHT
        }

        /**
         * Represents a combination of a tile and a direction.
         * Used to store possible tile placements with their corresponding directions.
         */
        public record TitleAndDirection(int Title, Direction direction) {

            /**
             * Gets the direction.
             *
             * @return the direction
             */
            public Direction getDirection() {
                return direction;
            }

            /**
             * Gets the tile index.
             *
             * @return the tile index
             */
            public int getTitle() {
                return Title;
            }

            /**
             * Calculates the adjacent position based on the current tile position and
             * direction.
             *
             * @param titlePos the current tile position
             * @return the adjacent position in the specified direction
             */
            public PointInt getPositionFromDirection(PointInt titlePos) {

                int nextX = titlePos.x(), nextY = titlePos.y;
                switch (direction) {
                    case TOP:
                        nextY--;
                        break;
                    case BOTTOM:
                        nextY++;
                        break;
                    case LEFT:
                        nextX--;
                        break;
                    case RIGHT:
                        nextX++;
                        break;
                }
                return new PointInt(nextX, nextY);
            }

        }

    }

    /**
     * Represents a point with integer coordinates in 2D space.
     * Used for positioning tiles on the board and managing grid coordinates.
     */
    public record PointInt(int x, int y) {
        /**
         * Constructs a new PointInt with the specified coordinates.
         *
         * @param x the x-coordinate
         * @param y the y-coordinate
         */
        public PointInt {
        }

        /**
         * Returns a string representation of this point in the format "(x, y)".
         *
         * @return a string representation of this point
         */
        @Override
        public String toString() {
            return "(" + x + ", " + y + ")";
        }
    }

    /**
     * Represents a circuit tile with its properties and image.
     * <p>
     * Each tile has connection information for each of its four sides (top, right,
     * bottom, left).
     * Each side has 3 zones that determine compatibility with adjacent tiles. The
     * zones are defined
     * by colors: G (Green), B (Blue), W (White), D (Dark).
     * </p>
     * <p>
     * If all the colors of a side match with an adjacent tile, they can be placed
     * together.
     * </p>
     */
    public static class Title {

        /**
         * Name of the original tile this tile is based on
         */
        private final String originalName;
        /**
         * Description of the tile's sides using color codes
         */
        private final String sidesDescription;
        /**
         * Flag to force horizontal mirroring for special cases
         */
        private final boolean forceHorizontalMirror;
        private final boolean noSameNeighbor;
        private final TitleSet set;
        /**
         * The image representation of this tile
         */
        protected Image image;
        /**
         * Characters representing the top side zones (left, center, right)
         */
        protected char[] top = new char[3];
        /**
         * Characters representing the right side zones (top, center, bottom)
         */
        protected char[] right = new char[3];
        /**
         * Characters representing the bottom side zones (left, center, right)
         */
        protected char[] bottom = new char[3];
        /**
         * Characters representing the left side zones (top, center, bottom)
         */
        protected char[] left = new char[3];

        /**
         * Constructs a new tile with all parameters specified.
         *
         * @param name                  the name of the tile
         * @param sidesDescription      the 8-character description of the tile's sides
         * @param derivedType           the transformation applied to this tile
         * @param forceHorizontalMirror flag to force horizontal mirroring
         */
        Title(TitleSet set, String name, String sidesDescription,
                boolean forceHorizontalMirror, boolean noSameNeighbor, Image tileImage) {

            this.set = set;
            this.noSameNeighbor = noSameNeighbor;
            this.forceHorizontalMirror = forceHorizontalMirror;
            this.originalName = name;
            this.sidesDescription = sidesDescription;

            if (sidesDescription.length() != 8) {
                throw new IllegalArgumentException("sidesDescription must have exactly 8 characters");
            }
            // Parse the sidesDescription into respective arrays
            // top: indices 0, 1, 2
            top[0] = sidesDescription.charAt(0);
            top[1] = sidesDescription.charAt(1);
            top[2] = sidesDescription.charAt(2);

            // right: indices 2, 3, 4 (shared corner with top)
            right[0] = sidesDescription.charAt(2);
            right[1] = sidesDescription.charAt(3);
            right[2] = sidesDescription.charAt(4);

            // bottom: indices 4, 5, 6 (shared corner with right)
            bottom[0] = sidesDescription.charAt(6);
            bottom[1] = sidesDescription.charAt(5);
            bottom[2] = sidesDescription.charAt(4);

            // left: indices 6, 7, 0 (shared corner with bottom and top)
            left[0] = sidesDescription.charAt(0);
            left[1] = sidesDescription.charAt(7);
            left[2] = sidesDescription.charAt(6);

            this.image = tileImage;

        }

        protected Title titleClone(String newSidesDescription, DerivedType newDerivedType) {
            Image newImage = transformImage(this.image, newDerivedType);
            return new Title(set, originalName, newSidesDescription, false, noSameNeighbor, newImage);
        }

        /**
         * Constructs a new base tile with no transformation.
         *
         * @param name             the name of the tile
         * @param sidesDescription the 8-character description of the tile's sides
         * @param noSameNeighbor   flag to prevent placing same tiles adjacent to each
         *                         other
         */
        public Title(TitleSet set, String name, String sidesDescription,
                boolean forceHorizontalMirror, boolean noSameNeighbor) {
            this(set, name, sidesDescription, forceHorizontalMirror, noSameNeighbor,
                    loadImageFromName(set, name, DerivedType.none));
        }

        /**
         * Constructs a new base tile with no transformation.
         *
         * @param name             the name of the tile
         * @param sidesDescription the 8-character description of the tile's sides
         * @param noSameNeighbor   flag to prevent placing same tiles adjacent to each
         *                         other
         */
        public Title(TitleSet set, String name, String sidesDescription, Boolean noSameNeighbor) {
            this(set, name, sidesDescription, false, noSameNeighbor, loadImageFromName(set, name, DerivedType.none));
        }

        /**
         * Constructs a new base tile with no transformation.
         *
         * @param name             the name of the tile
         * @param sidesDescription the 8-character description of the tile's sides
         */
        public Title(TitleSet set, String name, String sidesDescription) {
            this(set, name, sidesDescription, false);
        }

        private static Image transformImage(Image baseImage, DerivedType derivedType) {
            // Apply transformation based on derived type
            switch (derivedType) {
                case horizontalMirror:
                    return createMirroredHorizontally(baseImage);
                case verticalMirror:
                    return createMirroredVertically(baseImage);
                case clockwise90:
                    return createRotatedImage(baseImage);
                case clockwise180:
                    // Rotate 90 degrees twice
                    return createRotatedImage(createRotatedImage(baseImage));
                case getClockwise270:
                    // Rotate 90 degrees three times
                    return createRotatedImage(createRotatedImage(createRotatedImage(baseImage)));
                default:
                    return baseImage;
            }
        }

        private static Image loadImageFromName(TitleSet set, String name, DerivedType derivedType) {
            // Load base image
            try {
                String imagePath = "/" + set.getName() + "/" + name + ".png";
                java.net.URL resource = Title.class.getResource(imagePath);
                if (resource != null) {
                    Image baseImage = new Image(resource.toExternalForm());
                    return transformImage(baseImage, derivedType);
                } else {
                    System.err.println("Warning: Could not find image resource: " + imagePath);
                    // Create a default empty image if resource is not found
                    return new WritableImage(1, 1);
                }
            } catch (Exception e) {
                System.err.println("Warning: Failed to load image for tile: " + name);
                e.printStackTrace();
                // Create a default empty image if loading fails
                return new WritableImage(1, 1);
            }
        }

        protected TitleSet getSet() {
            return set;
        }

    }
}