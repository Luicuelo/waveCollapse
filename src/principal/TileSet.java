package principal;

import principal.Tile.Side;
import javafx.scene.image.Image;
import javafx.scene.image.PixelReader;
import javafx.scene.image.WritableImage;

import java.net.URL;
import java.util.ArrayList;
import java.util.Arrays;
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
 *   <li>{@link Sampled} extracts each window from
 *       {@code /sample/&lt;name&gt;.png}. For overlapping sets, the top and right
 *       sides are read from the inner lines; the "from bottom" variant starts
 *       with a tile from the sample's last row.</li>
 * </ul>
 *
 * <p>Base tiles are mirrored and rotated to generate their variants.</p>
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
    WATER(Sampled.plain("Water")),

    /** Sample: non overlapping windows, seeded from the bottom row of the sample. */
    FLOWERS(Sampled.fromBottomNoOverlap("Flowers2")),

    /** Sample: non overlapping windows, seeded from the bottom row of the sample. */
    SKYLINE(Sampled.fromBottomNoOverlap("Skyline")),

    /** Sample: non overlapping windows, seeded from the bottom row of the sample. */
    SCALED_MAZE(Sampled.overlapping("ScaledMaze")),

    /** Sample: overlapping windows. */
    SIMPLE_WALL(Sampled.overlapping("SimpleWall")),

    /** Sample: overlapping windows. */
    PATHS(Sampled.overlapping("Paths"));

    private final TilesSource tilesSource;

    /** Shared empty result for a side that has no compatible tile. */
    private static final int[] NO_OPTIONS = new int[0];

    /** Index published before the volatile write to {@link #tiles}. */
    private Map<SideKey, int[]> compatibleBySide;

    /**
     * Ascending index of every tile, published like {@link #compatibleBySide}
     * before the volatile write to {@link #tiles}. Shared and only read, it is
     * the whole domain a fully isolated cell starts from.
     */
    private int[] allTileIndices;

    /**
     * Ascending indices of the tiles that come from the sample's bottom row,
     * published like {@link #allTileIndices} before the volatile write to
     * {@link #tiles}. Empty for a set with no ground tiles, so seeding a
     * from-bottom set picks among them without rescanning every tile each time.
     */
    private int[] bottomTileIndices;

    /**
     * The loaded tiles, or {@code null} until they are needed.
     */
    private volatile Tile[] tiles;

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
        return getTiles().length;
    }

    /**
     * Gets the tile stored at the given index.
     *
     * @param index position of the tile
     * @return the tile, or {@code null} if the index is out of range
     */
    Tile get(int index) {
        Tile[] loaded = getTiles();
        if (index < 0 || index >= loaded.length) {
            return null;
        }
        return loaded[index];
    }

    /**
     * Picks a random tile index.
     *
     * @param random source of randomness
     * @return the index of a random tile, or {@code -1} if the set is empty
     */
    int randomTileIndex(Random random) {
        Tile[] loaded = getTiles();
        return loaded.length == 0 ? -1 : random.nextInt(loaded.length);
    }

    /**
     * Picks a random tile index among the tiles that come from the bottom row of
     * the sample, falling back to any tile when there is none.
     *
     * @param random source of randomness
     * @return the index of a random bottom tile, or {@code -1} if the set is empty
     */
    int randomBottomTileIndex(Random random) {
        getTiles();
        if (bottomTileIndices.length == 0) {
            return randomTileIndex(random);
        }
        return bottomTileIndices[random.nextInt(bottomTileIndices.length)];
    }

    /**
     * Indicates whether the set's patterns overlap.
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

    /** Distance between anchors before applying the screen zoom. */
    int getCellSize() {
        int size = getTileSize();
        return usesOverlap() ? (size - 1) * size : size;
    }

    /**
     * Returns the tiles that fit a neighbour already placed on one side. The
     * index keys each tile by the neighbour's facing side and the zones it must
     * meet there, so this is a direct lookup with no extra inversion.
     * The shared array is sorted ascending and must not be modified.
     *
     * @param facing    side of the neighbour that faces the candidate
     * @param signature zones that neighbour exposes on the facing side
     * @return indices compatible by side, without filtering names
     */
    int[] compatibleTiles(Side facing, String signature) {
        getTiles();
        return compatibleBySide.getOrDefault(new SideKey(facing, signature), NO_OPTIONS);
    }

    /**
     * Returns the index of every tile, ascending and shared. It is the domain of
     * a cell with no placed neighbour, so the caller must not modify it.
     *
     * @return every tile index, ordered as in the set
     */
    int[] allTileIndices() {
        getTiles();
        return allTileIndices;
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
     * @return the loaded tiles
     */
    private Tile[] getTiles() {
        Tile[] current = tiles;
        return current != null ? current : load();
    }

    /**
     * Reads the whole set: the base tiles, provided by its {@link TilesSource},
     * and then their mirrors and rotations. Only one thread gets to do the work,
     * and the rest simply wait for the finished list.
     *
     * @return the loaded tiles
     */
    private synchronized Tile[] load() {
        if (tiles != null) {
            return tiles;
        }

        List<Tile> building = new ArrayList<>();
        Map<SideKey, List<Integer>> index = new HashMap<>();
        Palette palette = new Palette();
        tilesSource.loadBaseTiles(building, index, palette);

        if (!tilesSource.derivesDuringLoad()) {
            // New variants can also produce other transformations.
            boolean added = true;
            while (added) {
                added = generateDerivedTiles(building, index, palette);
            }
        }

        compatibleBySide = freezeIndex(index);
        allTileIndices = ascendingIndices(building.size());
        bottomTileIndices = bottomIndices(building);
        tiles = building.toArray(Tile[]::new);
        return tiles;
    }

    /** Fills {@code 0..size-1} ascending: the shared full domain. */
    private static int[] ascendingIndices(int size) {
        int[] indices = new int[size];
        for (int i = 0; i < size; i++) {
            indices[i] = i;
        }
        return indices;
    }

    /** Collects, ascending, the positions of the tiles flagged as bottom. */
    private static int[] bottomIndices(List<Tile> building) {
        int[] indices = new int[building.size()];
        int count = 0;
        for (int i = 0; i < building.size(); i++) {
            if (building.get(i).isBottomTile()) {
                indices[count++] = i;
            }
        }
        return Arrays.copyOf(indices, count);
    }

    /**
     * Freezes the mutable building index into ascending {@code int} arrays. Each
     * list already holds the tile positions in loading order, so sorting keeps
     * them ascending; the domain later intersects those arrays by merging.
     */
    private static Map<SideKey, int[]> freezeIndex(Map<SideKey, List<Integer>> index) {
        Map<SideKey, int[]> frozen = new HashMap<>(index.size());
        index.forEach((key, positions) -> {
            int[] ascending = positions.stream().mapToInt(Integer::intValue).toArray();
            Arrays.sort(ascending);
            frozen.put(key, ascending);
        });
        return Map.copyOf(frozen);
    }

    /**
     * Adds the mirrors and rotations of every tile already loaded.
     *
     * <p>Ground tiles are the exception: they only ever get their horizontal
     * mirror, so the soil border can never end up on a side or on the top.
     * The rule is driven by the bottom flag, not by the kind of source, so it
     * also covers the sets derived here instead of during their own loading.</p>
     *
     * @param building list the derived tiles are added to
     * @param index    index updated when each tile is added
     * @return {@code true} if at least one distinct tile was added
     */
    private boolean generateDerivedTiles(List<Tile> building, Map<SideKey, List<Integer>> index,
                                         Palette palette) {
        int initialSize = building.size();
        boolean added = false;

        for (int i = 0; i < initialSize; i++) {
            Tile original = building.get(i);
            for (Transform transform : Transform.values()) {
                if (original.isBottomTile() && transform != Transform.HORIZONTAL_MIRROR) {
                    continue;
                }
                added |= addDerived(building, index, original, transform, palette);
            }
        }
        return added;
    }

    /**
     * Adds a derived tile unless an equivalent one is already present.
     *
     * <p>The scope of the duplicate check is decided by the {@link TilesSource}:
     * hand declared sets only compare against tiles with the same name, so two
     * different tiles may share a border. Non-overlapping sampled sets also
     * compare every pixel in the image.</p>
     *
     * @param building  list the derived tile is added to
     * @param index     index updated when the tile is added
     * @param original  tile being derived
     * @param transform transformation to apply
     * @param palette   palette shared during loading
     * @return {@code true} if the derived tile was a new one
     */
    private boolean addDerived(List<Tile> building, Map<SideKey, List<Integer>> index, Tile original,
                               Transform transform, Palette palette) {
        String onlyForName = tilesSource.dedupesWithinName() ? original.getName() : null;
        Image image = transform.applyTo(original.getImage());
        String newSides = tilesSource.describeTransformed(original, transform, image, palette);
        boolean alreadyExists = findMatchingTile(building, newSides, onlyForName, image) != null;
        boolean mandatory =
                original.forcesHorizontalMirror() && transform == Transform.HORIZONTAL_MIRROR;

        if (alreadyExists && !mandatory) {
            return false;
        }

        addTile(building, index, original.derive(newSides, image));
        // A mandatory mirror may repeat an existing tile: it is added, but it is
        // not a new distinct one, so only a genuinely new tile reports true.
        return !alreadyExists;
    }

    /** Adds the tile to the list and the side index in the same step. */
    private static void addTile(List<Tile> building, Map<SideKey, List<Integer>> index, Tile tile) {
        int position = building.size();
        building.add(tile);
        for (Side side : Side.ALL) {
            // Bottom tiles (ground) cannot have neighbors BELOW them (Side.BOTTOM).
            // They MUST allow neighbors ABOVE (Side.TOP) for flowers/grass.
            if (tile.isBottomTile() && side == Side.BOTTOM) {
                continue;
            }
            String signature = new String(side.zonesOf(tile));
            // Key by the neighbour's facing side (side.opposite) and the zones
            // it must meet, since the candidate carries `signature` on its own
            // `side`. compatibleTiles then looks this up directly.
            index.computeIfAbsent(new SideKey(side.opposite(), signature), key -> new ArrayList<>()).add(position);
        }
    }

    /**
     * Finds an equivalent description. Non-overlapping samples also compare the
     * entire image because signatures do not include all of its pixels.
     */
    private static Tile findMatchingTile(List<Tile> building, String sides, String onlyForName, Image image) {
        for (Tile tile : building) {
            if (onlyForName != null && !tile.getName().equals(onlyForName)) {
                continue;
            }
            if (tile.getSidesDescription().equals(sides)
                    && (onlyForName != null || TileImageOps.samePixels(tile.getImage(), image))) {
                return tile;
            }
        }
        return null;
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
     * <p>Each rotation uses the four oriented side descriptions.</p>
     *
     * @param sides the border description
     * @param turns number of quarter turns, clockwise
     * @return the rotated description
     */
    private static String rotateZones(String sides, int turns) {
        String rotated = sides;
        for (int i = 0; i < turns; i++) {
            rotated = remapZones(rotated, ROTATE_CLOCKWISE);
        }
        return rotated;
    }

    /** Key for the neighbour's side and the zones required by the candidate. */
    private record SideKey(Side side, String signature) {
    }

    /**
     * The description of a set: how it is called, how big its tiles are and
     * where those tiles come from.
     *
     * <p>This is the single variation point of a tile set, and it holds every
     * piece of data the loading needs, so {@link #loadBaseTiles(List, Map, Palette)} can work
     * from the list and index it fills and nothing else. The source can also
     * generate its own variants when deduplication requires it.</p>
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
         * Loads the tiles and, if the source requires it, their variants as well.
         *
         * @param building list the tiles are added to
         * @param index    index updated when each tile is added
         * @param palette  palette shared during loading
         */
        void loadBaseTiles(List<Tile> building, Map<SideKey, List<Integer>> index, Palette palette);

        /** Indicates whether the source generates its variants during loading. */
        default boolean derivesDuringLoad() {
            return false;
        }

        /** Calculates the sides of a transformed image. */
        default String describeTransformed(Tile original, Transform transform, Image image, Palette palette) {
            String sides = original.getSidesDescription();
            return switch (transform) {
                case HORIZONTAL_MIRROR -> remapZones(sides, MIRROR_HORIZONTAL);
                case VERTICAL_MIRROR -> remapZones(sides, MIRROR_VERTICAL);
                case ROTATE_90 -> rotateZones(sides, 1);
                case ROTATE_180 -> rotateZones(sides, 2);
                case ROTATE_270 -> rotateZones(sides, 3);
            };
        }

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
         * Indicates whether the set's patterns overlap.
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
        public void loadBaseTiles(List<Tile> building, Map<SideKey, List<Integer>> index, Palette palette) {
            for (Definition definition : definitions) {
                addTile(building, index,
                        new Tile(definition.name(), expandSides(definition.sidesDescription()),
                                definition.forcesHorizontalMirror(), definition.forbidsSameNeighbor(),
                                loadGraphic(definition.name())));
            }
        }

        /** Expands the traditional eight-zone description into four sides. */
        private static String expandSides(String sides) {
            if (sides.length() != 8) {
                throw new IllegalArgumentException("A hand-written tile must have eight zones");
            }
            int[] positions = {0, 1, 2, 2, 3, 4, 6, 5, 4, 0, 7, 6};
            StringBuilder expanded = new StringBuilder(Tile.SIDES_LENGTH);
            for (int position : positions) {
                expanded.append(sides.charAt(position));
            }
            return expanded.toString();
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
     * A set whose tiles are the distinct windows of a sample.
     * Overlapping patterns are deduplicated by their sides after generating their variants.
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
         * A non overlapping sample whose generation is seeded from its bottom row.
         *
         * @param sampleName name of the sample image, without extension
         * @return the description of that set
         */
        private static Sampled fromBottomNoOverlap(String sampleName) {
            return new Sampled(sampleName, TILESIZE, false, true);
        }

        @Override
        public void loadBaseTiles(List<Tile> building, Map<SideKey, List<Integer>> index, Palette palette) {
            Image sample = loadResource("/sample/" + folderName + ".png");
            if (sample == null) {
                return;
            }

            PixelReader reader = sample.getPixelReader();
            int width = (int) sample.getWidth();
            int height = (int) sample.getHeight();

            if (usesOverlap) {
                loadOverlappingTiles(building, index, palette, reader, width, height);
            } else {
                loadPlainTiles(building, index, palette, reader, width, height);
            }
        }

        /**
         * Cuts every non overlapping window out of the sample, skipping the ones
         * whose sides already exist, and flags the windows of the last row as
         * ground when the set is seeded from the bottom.
         */
        private void loadPlainTiles(List<Tile> building, Map<SideKey, List<Integer>> index,
                                    Palette palette, PixelReader reader, int width, int height) {
            for (int y = 0; y + tileSize <= height; y++) {
                for (int x = 0; x + tileSize <= width; x++) {
                    WritableImage pattern = TileImageOps.copyRegion(reader, x, y, tileSize);
                    String sides = describeSides(palette, pattern.getPixelReader(), tileSize, false);
                    if (findMatchingTile(building, sides, null, pattern) != null) {
                        continue;
                    }
                    Tile tile = new Tile(folderName + "_" + x + "_" + y, sides, false, false, pattern);
                    if (startsFromBottom && y == height - tileSize) {
                        tile.markAsBottomTile();
                    }
                    addTile(building, index, tile);
                }
            }
        }

        /** Generates the permitted symmetries of every window, including duplicates. */
        private void loadOverlappingTiles(List<Tile> building, Map<SideKey, List<Integer>> index,
                                          Palette palette, PixelReader reader, int width, int height) {
            List<Tile> originals = new ArrayList<>();
            Map<String, Tile> bySignature = new HashMap<>();
            for (int y = 0; y + tileSize <= height; y++) {
                for (int x = 0; x + tileSize <= width; x++) {
                    Image image = TileImageOps.copyRegion(reader, x, y, tileSize);
                    String sides = describeSides(palette, image.getPixelReader(), tileSize, true);
                    Tile original = new Tile(folderName + "_" + x + "_" + y, sides, false, false, image);
                    if (startsFromBottom && y == height - tileSize) {
                        original.markAsBottomTile();
                    }
                    originals.add(original);
                    addOverlappingTile(original, building, index, bySignature);
                }
            }

            // Even if two originals share a signature, their mirrors may differ.
            for (Tile original : originals) {
                Image image = original.getImage();
                Image mirrored = TileImageOps.mirrorHorizontally(image);
                if (original.isBottomTile()) {
                    // Ground tiles are only mirrored, never rotated or flipped.
                    addOverlappingVariant(original, mirrored, building, index, palette, bySignature);
                    continue;
                }
                for (int turns = 0; turns < 4; turns++) {
                    if (turns > 0) {
                        addOverlappingVariant(original, TileImageOps.rotateClockwise(image, turns),
                                building, index, palette, bySignature);
                    }
                    addOverlappingVariant(original, TileImageOps.rotateClockwise(mirrored, turns),
                            building, index, palette, bySignature);
                }
            }
        }

        private void addOverlappingVariant(Tile original, Image image, List<Tile> building,
                                           Map<SideKey, List<Integer>> index, Palette palette,
                                           Map<String, Tile> bySignature) {
            String sides = describeSides(palette, image.getPixelReader(), tileSize, true);
            // derive keeps the bottom flag, so ground variants stay protected.
            addOverlappingTile(original.derive(sides, image), building, index, bySignature);
        }

        private static void addOverlappingTile(Tile tile, List<Tile> building,
                                               Map<SideKey, List<Integer>> index,
                                               Map<String, Tile> bySignature) {
            Tile existing = bySignature.putIfAbsent(tile.getSidesDescription(), tile);
            if (existing == null) {
                addTile(building, index, tile);
            } else if (tile.isBottomTile()) {
                existing.markAsBottomTile();
            }
        }

        @Override
        public boolean derivesDuringLoad() {
            return usesOverlap;
        }

        @Override
        public String describeTransformed(Tile original, Transform transform, Image image, Palette palette) {
            return describeSides(palette, image.getPixelReader(), tileSize, usesOverlap);
        }

        /** Reads the four effective sides directly from the pixels. */
        private static String describeSides(Palette palette, PixelReader pattern, int tileSize, boolean overlap) {
            int inner = tileSize / 2;
            int last = tileSize - 1;
            int top = overlap ? inner : 0;
            int right = overlap ? inner : last;
            int[] positions = {0, inner, last};
            StringBuilder description = new StringBuilder(Tile.SIDES_LENGTH);
            for (int x : positions) {
                description.append(palette.letterFor(pattern.getArgb(x, top)));
            }
            for (int y : positions) {
                description.append(palette.letterFor(pattern.getArgb(right, y)));
            }
            for (int x : positions) {
                description.append(palette.letterFor(pattern.getArgb(x, last)));
            }
            for (int y : positions) {
                description.append(palette.letterFor(pattern.getArgb(0, y)));
            }
            return description.toString();
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
     * Transformations of tile graphics and their side descriptions.
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
    private static final int[] MIRROR_VERTICAL = {6, 7, 8, 5, 4, 3, 0, 1, 2, 11, 10, 9};

    /**
     * Indexes of the border zones when a tile is mirrored horizontally (left
     * and right swapped). {@code mirrored[i] = original[MIRROR_HORIZONTAL[i]]}.
     */
    private static final int[] MIRROR_HORIZONTAL = {2, 1, 0, 9, 10, 11, 8, 7, 6, 3, 4, 5};

    /** Side mapping for a 90-degree clockwise rotation. */
    private static final int[] ROTATE_CLOCKWISE = {11, 10, 9, 0, 1, 2, 5, 4, 3, 6, 7, 8};
}
