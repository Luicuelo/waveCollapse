package principal;

import javafx.scene.image.Image;
import javafx.scene.image.PixelReader;
import javafx.scene.image.WritableImage;

import java.net.URL;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;

/**
 * The tile sets bundled with the application, together with the tiles they are
 * made of.
 *
 * <p>A set is two things at once, and that is why they live together here: the
 * description of where its graphics are and how it has to be read, and the
 * tiles that come out of that description. The tiles are loaded the first time
 * they are asked for and never change afterwards, so the set can be shared by
 * every generation instead of being rebuilt on each restart.</p>
 *
 * <p>What changes from one set to another is only where the base tiles come
 * from, so that is the only thing that is polymorphic. It is delegated to a
 * {@link TilesSource}:</p>
 *
 * <ul>
 *   <li>{@link HandWritten} sets declare their tiles one by one and load their
 *       graphics from {@code /&lt;folder&gt;/&lt;name&gt;.png}.</li>
 *   <li>{@link Sampled} sets take every window of
 *       {@code /sample/&lt;name&gt;.png}. When the patterns overlap, neighbouring
 *       tiles share one pixel, so the middle row and column take part in the
 *       compatibility test too; the "from bottom" variant seeds the generation
 *       from the bottom row of the sample (useful for scrolling backgrounds).</li>
 * </ul>
 *
 * <p>Once the base tiles exist, the mirrors and rotations of every one of them
 * are derived until no new border description appears. Derived tiles are
 * inserted right after the last tile with the same name, so all the variants of
 * a tile stay together.</p>
 */
enum TileSet {

    /** Electronic traces, vias and components. */
    CIRCUIT(new HandWritten("circuit", 14,
            new Definition("bridge", "GBGWGBGW"),
            new Definition("component", "DDDDDDDD"),
            new Definition("connection", "GBGGDDDG"),
            new Definition("corner", "GGGGGGDG"),
            // dskew has no vertical mirror, so its horizontal mirror is forced.
            new Definition("dskew", "GBGBGBGB", false, true),
            new Definition("ecomponent", "GGGGDDDG"),
            new Definition("skew", "GBGBGGGG"),
            new Definition("substrate", "GGGGGGGG"),
            new Definition("t", "GGGBGBGB"),
            new Definition("track", "GBGGGBGG"),
            new Definition("transition", "GWGGGBGG"),
            new Definition("turn", "GBGBGGGG"),
            new Definition("viad", "GGGBGGGB"),
            new Definition("vias", "GBGGGGGG"),
            new Definition("wire", "GGGWGGGW"))),

    /** Stone walls, rivers, roads and bridges. */
    CASTLE(new HandWritten("castle", 7,
            new Definition("bridge", "GBGYGBGY"),
            new Definition("ground", "GGGGGGGG"),
            new Definition("river", "GBGGGBGG"),
            new Definition("riverturn", "GBGBGGGG"),
            new Definition("road", "GYGGGYGG"),
            new Definition("roadturn", "GYGYGGGG"),
            new Definition("t", "GGGYGYGY"),
            new Definition("tower", "GWGWGGGG", true),
            new Definition("wall", "GWGGGWGG"),
            new Definition("wallriver", "GWGBGWGB"),
            new Definition("wallroad", "GWGYGWGY"))),

    /** Celtic knots. */
    KNOTS(new HandWritten("knots", 10,
            new Definition("corner", "WCWCWWWW"),
            new Definition("cross", "WCWCWCWC"),
            new Definition("empty", "WWWWWWWW"),
            new Definition("line", "WWWCWWWC"),
            new Definition("t", "WWWCWCWC"))),

    /** Minimal black and white line work. */
    SIMPLE(new HandWritten("simple", 3,
            new Definition("blank", "WWWWWWWW"),
            new Definition("corner", "WCWCWWWW"),
            new Definition("cross", "WCWCWCWC"),
            new Definition("line", "WWWCWWWC"),
            new Definition("t", "WWWCWCWC"))),

    /** Rooms, corridors, doors, glass walls and furniture. */
    FLOORPLAN(new HandWritten("floorPlan", 9,
            new Definition("div", "GGGLGGGL"),
            new Definition("divt", "GGGLGLGL"),
            new Definition("divturn", "GLGLGGGG"),
            new Definition("door", "GGGLGGGL"),
            new Definition("empty", "WWWWWWWW"),
            new Definition("floor", "GGGGGGGG"),
            new Definition("glass", "GGGMWWWM"),
            new Definition("halfglass", "GGGMWWWD"),
            new Definition("in", "GGGGGDWD"),
            new Definition("out", "WDGDWWWW"),
            new Definition("stairs", "GGGLGSGL"),
            new Definition("table", "GGGGGGGG", true),
            new Definition("vent", "WWWWWWWW", true),
            new Definition("w", "GGGDWWWD", true),
            new Definition("wall", "GGGDWWWD"),
            new Definition("walldiv", "GLGDWWWD"),
            new Definition("window", "GGGDWWWD", true))),

    /** Blueprint style rooms. */
    ROOMS(new HandWritten("rooms", 3,
            new Definition("bend", "WBBBWWWW"),
            new Definition("corner", "BBWBBBBB"),
            new Definition("corridor", "BWBBBWBB"),
            new Definition("door", "WWWBBWBB"),
            new Definition("empty", "WWWWWWWW"),
            new Definition("side", "BBBBWWWB"),
            new Definition("t", "BBBWBWBW"),
            new Definition("turn", "BWBWBBBB"),
            new Definition("wall", "BBBBBBBB"))),

    /** Black and white circles that tile into each other. */
    CIRCLES(new HandWritten("circles", 32,
            new Definition("b", "WBWBWBWB"),
            new Definition("w", "BWBWBWBW"),
            new Definition("b_half", "WBWWWWWW"),
            new Definition("w_half", "BWBBBBBB"),
            new Definition("b_i", "WBWWWBWW"),
            new Definition("w_i", "BWBBBWBB"),
            new Definition("b_quarter", "WBWBWWWW"),
            new Definition("w_quarter", "BWBWBBBB"))),

    /** Sample: non overlapping windows. */
    NOT_KNOT(Sampled.plain("NotKnot")),

    /** Sample: non overlapping windows. */
    LINK(Sampled.plain("Link")),

    /** Sample: non overlapping windows. */
    KNOT(Sampled.plain("Knot")),

    /** Sample: non overlapping windows. */
    MAZE(Sampled.plain("Maze")),

    /** Sample: non overlapping windows. */
    COLORED_CITY(Sampled.plain("ColoredCity")),

    /** Sample: non overlapping windows. */
    CITY(Sampled.plain("City")),

    /** Sample: non overlapping windows. */
    SKYLINE(Sampled.plain("Skyline")),

    /** Sample: non overlapping windows. */
    WATER(Sampled.plain("Water")),

    /** Sample: overlapping windows, seeded from the bottom row of the sample. */
    FLOWERS(Sampled.fromBottom("Flowers")),

    /** Sample: overlapping windows. */
    SIMPLE_WALL(Sampled.overlapping("SimpleWall")),

    /** Sample: overlapping windows. */
    PATHS(Sampled.overlapping("Paths"));

    private final TilesSource tilesSource;

    /**
     * The loaded tiles, or {@code null} until they are needed. It is replaced
     * once by an immutable list, so readers never see a half filled set.
     */
    private volatile List<Tile> tiles;

    /**
     * Describes a set. The tiles are only loaded when they are first asked for.
     *
     * @param tilesSource name, tile size and base tiles of the set
     */
    TileSet(TilesSource tilesSource) {
        this.tilesSource = tilesSource;
    }

    /**
     * Gets the number of tiles available, base and derived.
     *
     * @return the number of tiles in the set
     */
    int size() {
        return getTiles().size();
    }

    /**
     * Gets the tile stored at the given index.
     *
     * @param index position of the tile
     * @return the tile, or {@code null} if the index is out of range
     */
    Tile get(int index) {
        List<Tile> loaded = getTiles();
        if (index < 0 || index >= loaded.size()) {
            return null;
        }
        return loaded.get(index);
    }

    /**
     * Picks a random tile index.
     *
     * @param random source of randomness
     * @return the index of a random tile, or {@code -1} if the set is empty
     */
    int randomTileIndex(Random random) {
        List<Tile> loaded = getTiles();
        return loaded.isEmpty() ? -1 : random.nextInt(loaded.size());
    }

    /**
     * Picks a random tile index among the tiles that come from the bottom row of
     * the sample, falling back to any tile when there is none.
     *
     * @param random source of randomness
     * @return the index of a random bottom tile, or {@code -1} if the set is empty
     */
    int randomBottomTileIndex(Random random) {
        List<Tile> loaded = getTiles();
        List<Integer> bottomTiles = new ArrayList<>();
        for (int i = 0; i < loaded.size(); i++) {
            if (loaded.get(i).isBottomTile()) {
                bottomTiles.add(i);
            }
        }
        if (bottomTiles.isEmpty()) {
            return randomTileIndex(random);
        }
        return bottomTiles.get(random.nextInt(bottomTiles.size()));
    }

    /**
     * Tells whether this set uses overlapping patterns, in which case the middle
     * row and column of a tile are compared too.
     *
     * @return {@code true} if the set overlaps its patterns
     */
    boolean usesOverlap() {
        return tilesSource.usesOverlap();
    }

    /**
     * Tells whether the generation has to start with a bottom row tile.
     *
     * @return {@code true} if the first tile must be a bottom tile
     */
    boolean startsFromBottom() {
        return tilesSource.startsFromBottom();
    }

    /**
     * Gets the name of the folder holding the tile images. For sample based
     * sets it is also the name of the image file.
     *
     * @return the folder name of the set
     */
    String getFolderName() {
        return tilesSource.folderName();
    }

    /**
     * Gets the size, in pixels, of a tile of this set. For sample based sets it
     * is the size of the sampled pattern.
     *
     * @return the tile size in pixels
     */
    int getTileSize() {
        return tilesSource.tileSize();
    }

    /**
     * Returns the folder name, which reads better than the constant name in the
     * set selector of the user interface.
     *
     * @return the folder name of the set
     */
    @Override
    public String toString() {
        return getFolderName();
    }

    /**
     * Gets the tiles of this set, loading them on first use.
     *
     * @return the loaded tiles, immutable and safe to share between threads
     */
    private List<Tile> getTiles() {
        List<Tile> current = tiles;
        return current != null ? current : load();
    }

    /**
     * Reads the whole set: the base tiles, provided by its {@link TilesSource},
     * and then their mirrors and rotations. Only one thread gets to do the work,
     * and the rest simply wait for the finished list.
     *
     * @return the loaded tiles
     */
    private synchronized List<Tile> load() {
        if (tiles != null) {
            return tiles;
        }

        List<Tile> building = new ArrayList<>();
        tilesSource.loadBaseTiles(building);

        // Deriving may create borders that were still unknown, which in turn can
        // be derived again, so repeat until nothing new shows up.
        boolean added = true;
        while (added) {
            added = generateDerivedTiles(building);
        }

        tiles = List.copyOf(building);
        return tiles;
    }

    /**
     * Adds the mirrors and rotations of every tile already loaded.
     *
     * @param building list the derived tiles are added to
     * @return {@code true} if at least one tile with a new border was added
     */
    private boolean generateDerivedTiles(List<Tile> building) {
        List<Tile> snapshot = List.copyOf(building);
        boolean added = false;

        for (Tile original : snapshot) {
            String sides = original.getSidesDescription();
            added |= addDerived(building, original, Transform.HORIZONTAL_MIRROR,
                    remapZones(sides, MIRROR_HORIZONTAL));
            added |= addDerived(building, original, Transform.VERTICAL_MIRROR,
                    remapZones(sides, MIRROR_VERTICAL));
            added |= addDerived(building, original, Transform.ROTATE_90, rotateZones(sides, 1));
            added |= addDerived(building, original, Transform.ROTATE_180, rotateZones(sides, 2));
            added |= addDerived(building, original, Transform.ROTATE_270, rotateZones(sides, 3));
        }
        return added;
    }

    /**
     * Adds a derived tile unless an equivalent one is already present.
     *
     * <p>The scope of the duplicate check is decided by the {@link TilesSource}:
     * hand declared sets only compare against tiles with the same name, so two
     * different tiles may share a border, while sampled sets compare against the
     * whole list because for them the border is the identity of the tile.</p>
     *
     * @param building  list the derived tile is added to
     * @param original  tile being derived
     * @param transform transformation to apply
     * @param newSides  border description of the derived tile
     * @return {@code true} if the derived tile was a new one
     */
    private boolean addDerived(List<Tile> building, Tile original, Transform transform, String newSides) {
        String onlyForName = tilesSource.dedupesWithinName() ? original.getName() : null;

        boolean alreadyExists =
                descriptionExists(building, newSides, original.getMidDescription(), onlyForName);
        boolean mandatory =
                original.forcesHorizontalMirror() && transform == Transform.HORIZONTAL_MIRROR;

        if (alreadyExists && !mandatory) {
            return false;
        }

        int insertIndex = lastIndexWithName(building, original.getName()) + 1;
        building.add(insertIndex, original.derive(newSides, transform.applyTo(original.getImage())));
        return !alreadyExists;
    }

    /**
     * Tells whether a tile with the given border and centre is already loaded.
     *
     * @param building    list of tiles loaded so far
     * @param sides       border description to look for
     * @param mid         centre zone to look for
     * @param onlyForName when not {@code null}, only tiles with this name are
     *                    taken into account
     * @return {@code true} if such a tile exists
     */
    private static boolean descriptionExists(List<Tile> building, String sides, String mid,
                                             String onlyForName) {
        for (Tile tile : building) {
            if (onlyForName != null && !tile.getName().equals(onlyForName)) {
                continue;
            }
            if (tile.getSidesDescription().equals(sides) && tile.getMidDescription().equals(mid)) {
                return true;
            }
        }
        return false;
    }

    /**
     * Finds the position of the last tile with the given name.
     *
     * @param building list of tiles loaded so far
     * @param name     name to look for
     * @return the last index using that name, or {@code -1} if there is none
     */
    private static int lastIndexWithName(List<Tile> building, String name) {
        for (int i = building.size() - 1; i >= 0; i--) {
            if (building.get(i).getName().equals(name)) {
                return i;
            }
        }
        return -1;
    }

    /**
     * Loads an image from the classpath.
     *
     * @param path absolute path inside the classpath, extension included
     * @return the image, or {@code null} when it cannot be loaded
     */
    private static Image loadResource(String path) {
        try {
            URL resource = TileSet.class.getResource(path);
            if (resource != null) {
                return new Image(resource.toExternalForm());
            }
            System.err.println("Warning: image not found on the classpath: " + path);
        } catch (RuntimeException e) {
            System.err.println("Warning: could not load image " + path + ": " + e.getMessage());
        }
        return null;
    }

    /**
     * Rewrites a border description by moving every zone to the position given
     * by {@code indexMap}.
     *
     * @param sides    the border description
     * @param indexMap source index of every zone
     * @return the remapped description
     */
    private static String remapZones(String sides, int[] indexMap) {
        char[] remapped = new char[Tile.SIDES_LENGTH];
        for (int i = 0; i < Tile.SIDES_LENGTH; i++) {
            remapped[i] = sides.charAt(indexMap[i]);
        }
        return new String(remapped);
    }

    /**
     * Rotates a border description clockwise.
     *
     * <p>Every quarter turn shifts the eight zones six positions, which is the
     * same as saying the new top side is the old left side read upwards.</p>
     *
     * @param sides the border description
     * @param turns number of quarter turns, clockwise
     * @return the rotated description
     */
    private static String rotateZones(String sides, int turns) {
        int shift = (6 * turns) % Tile.SIDES_LENGTH;
        char[] rotated = new char[Tile.SIDES_LENGTH];
        for (int i = 0; i < Tile.SIDES_LENGTH; i++) {
            rotated[i] = sides.charAt((i + shift) % Tile.SIDES_LENGTH);
        }
        return new String(rotated);
    }

    /**
     * The description of a set: how it is called, how big its tiles are and
     * where those tiles come from.
     *
     * <p>This is the single variation point of a tile set, and it holds every
     * piece of data the loading needs, so {@link #loadBaseTiles(List)} can work
     * from the list it fills and nothing else. The loading itself, the derived
     * variants and the accessors are shared by every set, so no code has to ask
     * what kind of set it is looking at.</p>
     */
    private interface TilesSource {

        /**
         * Gets the name of the set: the folder holding its graphics, or the name
         * of the sample image.
         *
         * @return the name of the set
         */
        String folderName();

        /**
         * Gets the size, in pixels, of one tile of the set.
         *
         * @return the tile size in pixels
         */
        int tileSize();

        /**
         * Creates the base tiles of a set. The derived variants are added
         * afterwards by {@link TileSet}, never here.
         *
         * @param building list the tiles are added to
         */
        void loadBaseTiles(List<Tile> building);

        /**
         * Tells whether a tile counts as a duplicate only when it repeats the
         * name of an existing one. Hand declared sets say yes, because two of
         * their tiles may legitimately share a border.
         *
         * @return {@code true} to look for duplicates within the same name only
         */
        default boolean dedupesWithinName() {
            return false;
        }

        /**
         * Tells whether the patterns of the set overlap their neighbours, in
         * which case the middle row and column of a tile are compared as well.
         *
         * @return {@code true} for overlapping patterns
         */
        default boolean usesOverlap() {
            return false;
        }

        /**
         * Tells whether the generation has to start with a tile from the bottom
         * row of the sample instead of a random one.
         *
         * @return {@code true} when the first tile must be a bottom tile
         */
        default boolean startsFromBottom() {
            return false;
        }
    }

    /**
     * A set whose tiles are declared one by one, each with the name of its
     * graphic inside the folder of the set.
     *
     * @param folderName  folder holding the graphics, under {@code resources/}
     * @param tileSize    size of a tile in pixels
     * @param definitions base tiles of the set
     */
    private record HandWritten(String folderName, int tileSize,
                               List<Definition> definitions) implements TilesSource {

        private HandWritten(String folderName, int tileSize, Definition... definitions) {
            this(folderName, tileSize, List.of(definitions));
        }

        @Override
        public void loadBaseTiles(List<Tile> building) {
            for (Definition definition : definitions) {
                building.add(new Tile(definition.name(), definition.sidesDescription(), "",
                        definition.forcesHorizontalMirror(), definition.forbidsSameNeighbor(),
                        loadGraphic(definition.name())));
            }
        }

        @Override
        public boolean dedupesWithinName() {
            return true;
        }

        /**
         * Loads the graphic of a hand declared tile, falling back to a 1x1
         * placeholder when it cannot be found, so a missing file never breaks a
         * whole set.
         *
         * @param name name of the tile, without extension
         * @return the loaded image
         */
        private Image loadGraphic(String name) {
            Image image = loadResource("/" + folderName + "/" + name + ".png");
            return image != null ? image : new WritableImage(1, 1);
        }
    }

    /**
     * A set whose tiles are every window of a sample image. Windows whose border
     * (and centre) was already seen are skipped, which is what turns a bitmap
     * into a set of distinct tiles.
     *
     * @param folderName       name of the sample, without extension
     * @param tileSize         side of a window, in pixels
     * @param usesOverlap      whether neighbouring patterns share one pixel
     * @param startsFromBottom whether the generation starts from the bottom row
     */
    private record Sampled(String folderName, int tileSize, boolean usesOverlap,
                           boolean startsFromBottom) implements TilesSource {

        /** Side, in pixels, of the windows cut out of a sample. */
        private static final int TILESIZE = 3;

        /**
         * A sample whose windows do not overlap: neighbouring tiles touch but
         * share nothing.
         *
         * @param sampleName name of the sample image, without extension
         * @return the description of that set
         */
        private static Sampled plain(String sampleName) {
            return new Sampled(sampleName, TILESIZE, false, false);
        }

        /**
         * A sample whose windows share one pixel with their neighbours.
         *
         * @param sampleName name of the sample image, without extension
         * @return the description of that set
         */
        private static Sampled overlapping(String sampleName) {
            return new Sampled(sampleName, TILESIZE, true, false);
        }

        /**
         * An overlapping sample whose generation is seeded from its bottom row.
         *
         * @param sampleName name of the sample image, without extension
         * @return the description of that set
         */
        private static Sampled fromBottom(String sampleName) {
            return new Sampled(sampleName, TILESIZE, true, true);
        }

        @Override
        public void loadBaseTiles(List<Tile> building) {
            Image sample = loadResource("/sample/" + folderName + ".png");
            if (sample == null) {
                return;
            }

            PixelReader reader = sample.getPixelReader();
            int width = (int) sample.getWidth();
            int height = (int) sample.getHeight();
            Palette palette = new Palette();

            for (int y = 0; y + tileSize <= height; y++) {
                for (int x = 0; x + tileSize <= width; x++) {
                    WritableImage pattern = TileImageOps.copyRegion(reader, x, y, tileSize);
                    PixelReader patternReader = pattern.getPixelReader();
                    String sides = describeSides(palette, patternReader, tileSize);
                    String mid = describeMid(palette, patternReader, tileSize);

                    if (descriptionExists(building, sides, mid, null)) {
                        continue;
                    }

                    Tile tile = new Tile(folderName + "_" + x + "_" + y,
                            sides, mid, false, false, pattern);
                    if (usesOverlap && y == height - tileSize) {
                        tile.markAsBottomTile();
                    }
                    building.add(tile);
                }
            }
        }

        /**
         * Reads the eight border zones of a pattern, clockwise from the top-left
         * corner, and turns them into their letters.
         *
         * @param palette   letters assigned so far
         * @param pattern   pixels of one pattern
         * @param tileSize  side of the pattern, in pixels
         * @return the border description
         */
        private static String describeSides(Palette palette, PixelReader pattern, int tileSize) {
            int mid = tileSize / 2;
            int last = tileSize - 1;
            StringBuilder description = new StringBuilder(Tile.SIDES_LENGTH);
            description.append(palette.letterFor(pattern.getArgb(0, 0))); // top-left
            description.append(palette.letterFor(pattern.getArgb(mid, 0))); // top-center
            description.append(palette.letterFor(pattern.getArgb(last, 0))); // top-right
            description.append(palette.letterFor(pattern.getArgb(last, mid))); // right-center
            description.append(palette.letterFor(pattern.getArgb(last, last))); // bottom-right
            description.append(palette.letterFor(pattern.getArgb(mid, last))); // bottom-center
            description.append(palette.letterFor(pattern.getArgb(0, last))); // bottom-left
            description.append(palette.letterFor(pattern.getArgb(0, mid))); // left-center
            return description.toString();
        }

        /**
         * Reads the centre zone of a pattern.
         *
         * @param palette  letters assigned so far
         * @param pattern  pixels of one pattern
         * @param tileSize side of the pattern, in pixels
         * @return the letter of the centre colour
         */
        private static String describeMid(Palette palette, PixelReader pattern, int tileSize) {
            return palette.letterFor(pattern.getArgb(tileSize / 2, tileSize / 2));
        }
    }

    /**
     * Assigns a letter to every colour as it shows up, so the zones of a pattern
     * can be written as text ({@code A}, {@code B}, {@code C}...). A palette only
     * lives while its sample is being read, and every set gets its own, so the
     * letters of a set are stable for its whole life.
     */
    private static final class Palette {

        private final Map<Integer, String> lettersByColor = new HashMap<>();
        private char nextLetter = 'A';

        /**
         * Gets the letter of a colour, assigning the next free one if it is new.
         *
         * @param argb colour in {@code 0xAARRGGBB} format
         * @return the single character description of that colour
         */
        private String letterFor(int argb) {
            String letter = lettersByColor.get(argb);
            if (letter == null) {
                letter = String.valueOf(nextLetter++);
                lettersByColor.put(argb, letter);
            }
            return letter;
        }
    }

    /**
     * Description of a hand declared tile. Only hand written sets use it, which
     * is why it lives next to their declarations.
     *
     * @param name                   name of the tile, matches its file name
     * @param sidesDescription       eight zones read clockwise from the top-left
     * @param forbidsSameNeighbor    whether the tile rejects neighbours with its
     *                               own name
     * @param forcesHorizontalMirror whether the horizontal mirror is mandatory
     */
    private record Definition(String name, String sidesDescription,
                              boolean forbidsSameNeighbor, boolean forcesHorizontalMirror) {

        private Definition(String name, String sidesDescription) {
            this(name, sidesDescription, false, false);
        }

        private Definition(String name, String sidesDescription, boolean forbidsSameNeighbor) {
            this(name, sidesDescription, forbidsSameNeighbor, false);
        }
    }

    /**
     * The variant of a tile the set derives from it: the border description is
     * remapped by the set and the graphic by {@link TileImageOps}.
     */
    private enum Transform {

        /** Mirror across the vertical axis: left and right are swapped. */
        HORIZONTAL_MIRROR,
        /** Mirror across the horizontal axis: top and bottom are swapped. */
        VERTICAL_MIRROR,
        /** Quarter turn clockwise. */
        ROTATE_90,
        /** Half turn. */
        ROTATE_180,
        /** Three quarter turns clockwise. */
        ROTATE_270;

        /**
         * Applies this transformation to a tile graphic.
         *
         * @param source graphic to transform
         * @return the transformed graphic
         */
        private Image applyTo(Image source) {
            return switch (this) {
                case HORIZONTAL_MIRROR -> TileImageOps.mirrorHorizontally(source);
                case VERTICAL_MIRROR -> TileImageOps.mirrorVertically(source);
                case ROTATE_90 -> TileImageOps.rotateClockwise(source, 1);
                case ROTATE_180 -> TileImageOps.rotateClockwise(source, 2);
                case ROTATE_270 -> TileImageOps.rotateClockwise(source, 3);
            };
        }
    }

    /**
     * Indexes of the border zones when a tile is mirrored vertically (top and
     * bottom swapped). {@code mirrored[i] = original[MIRROR_VERTICAL[i]]}.
     */
    private static final int[] MIRROR_VERTICAL = {6, 5, 4, 3, 2, 1, 0, 7};

    /**
     * Indexes of the border zones when a tile is mirrored horizontally (left
     * and right swapped). {@code mirrored[i] = original[MIRROR_HORIZONTAL[i]]}.
     */
    private static final int[] MIRROR_HORIZONTAL = {2, 1, 0, 7, 6, 5, 4, 3};
}
