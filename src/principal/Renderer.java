package principal;

import javafx.scene.canvas.GraphicsContext;
import javafx.scene.image.Image;
import javafx.scene.paint.Color;

import java.util.Queue;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Draws the board on a JavaFX canvas.
 *
 * <p>Drawing is decoupled from the generation: the solver reports every change
 * to a listener given by this class, which stores the change in a queue and
 * paints one action per frame. That is what makes the collapse visible step by
 * step, but it also means the queue can run ahead of the canvas, so each action
 * carries both the tile index and the tile set it belongs to instead of looking
 * them up when the action is finally painted.</p>
 *
 * <p>The queue is a {@link ConcurrentLinkedQueue} because it is filled from the
 * solver thread and drained from the JavaFX thread. Each generation draws
 * through its own {@linkplain #newSession() session}: a generation that has
 * been thrown away may still queue a few actions while it dies out, and the
 * session token is what keeps them from being painted over the new board.</p>
 */
final class Renderer {

    /** A tile of an overlapping set is as wide as this many cells. */
    private static final int OVERLAP_SCALE = 3;

    /**
     * A pending drawing operation.
     */
    private interface TileAction {

        /**
         * Gets the drawing session that produced this operation.
         *
         * @return the session token
         */
        long session();

        /**
         * Performs the operation on the canvas.
         *
         * @param renderer renderer holding the graphics context
         */
        void execute(Renderer renderer);
    }

    /**
     * Paints a tile.
     *
     * @param session   drawing session of the generation that placed the tile
     * @param x         column
     * @param y         row
     * @param tileIndex index of the tile in the set
     * @param set       tile set the index belongs to
     */
    private record DrawTileAction(long session, int x, int y, int tileIndex, TileSet set)
            implements TileAction {

        @Override
        public void execute(Renderer renderer) {
            renderer.paintTile(set, tileIndex, x, y);
        }
    }

    /**
     * Clears the area covered by a tile.
     *
     * @param session   drawing session of the generation that removed the tile
     * @param x         column
     * @param y         row
     * @param tileIndex index the removed tile had in the set
     * @param set       tile set the index belongs to
     */
    private record EraseTileAction(long session, int x, int y, int tileIndex, TileSet set)
            implements TileAction {

        @Override
        public void execute(Renderer renderer) {
            renderer.eraseTile(set, tileIndex, x, y);
        }
    }

    private final GraphicsContext gc;
    private final Color backgroundColor;
    private final Queue<TileAction> pendingActions = new ConcurrentLinkedQueue<>();
    private final AtomicLong session = new AtomicLong();
    private int pixelSize;

    /**
     * Creates a renderer.
     *
     * @param gc              graphics context of the canvas (must not be null)
     * @param backgroundColor colour used to clear the canvas
     * @throws IllegalArgumentException if the graphics context is null
     */
    Renderer(GraphicsContext gc, Color backgroundColor) {
        if (gc == null) {
            throw new IllegalArgumentException("GraphicsContext cannot be null");
        }
        this.gc = gc;
        this.backgroundColor = backgroundColor;
    }

    /**
     * Sets the zoom of the drawing, in screen pixels per tile pixel.
     *
     * @param pixelSize size of a tile pixel on screen
     */
    void setPixelSize(int pixelSize) {
        this.pixelSize = pixelSize;
    }

    /**
     * Opens the drawing session of a new generation.
     *
     * <p>Every pending action is dropped and the session token is advanced, so
     * the operations a previous generation still queues while it dies out are
     * discarded instead of being painted over the new board.</p>
     *
     * @return the listener the new generation has to report to
     */
    WfcSolver.BoardListener newSession() {
        long token = session.incrementAndGet();
        pendingActions.clear();
        return new Session(token);
    }

    /**
     * Paints the whole canvas with the background colour.
     */
    void clearCanvas() {
        double width = gc.getCanvas().getWidth();
        double height = gc.getCanvas().getHeight();
        gc.clearRect(0, 0, width, height);
        gc.setFill(backgroundColor);
        gc.fillRect(0, 0, width, height);
    }

    /**
     * Executes the next pending action, if any. Called once per frame by the
     * animation timer.
     */
    void updateFrame() {
        TileAction action;
        while ((action = pendingActions.poll()) != null) {
            if (action.session() != session.get()) {
                // Left over by a generation that was thrown away before it
                // could be painted: the board on screen no longer holds it.
                continue;
            }
            action.execute(this);
            return;
        }
    }

    /**
     * Paints a tile, scaled so each of its pixels becomes a
     * {@link #pixelSize} square on screen.
     */
    private void paintTile(TileSet set, int tileIndex, int x, int y) {
        Tile tile = set.get(tileIndex);
        if (tile == null) {
            return;
        }
        Image image = tile.getImage();
        if (image == null) {
            return;
        }

        boolean overlap = set.usesOverlap();
        int cell = cellPixels(set, overlap);
        double screenX = (double) x * cell;
        double screenY = (double) y * cell;
        // Overlapping patterns are drawn OVERLAP_SCALE times bigger so that they
        // cover the neighbouring cells they share pixels with.
        double zoom = (double) pixelSize * (overlap ? OVERLAP_SCALE : 1);

        gc.setImageSmoothing(false);
        gc.drawImage(image, screenX, screenY, image.getWidth() * zoom, image.getHeight() * zoom);
    }

    /**
     * Clears the area covered by a tile with the background colour.
     */
    private void eraseTile(TileSet set, int tileIndex, int x, int y) {
        if (set.get(tileIndex) == null) {
            return;
        }
        int cell = cellPixels(set, set.usesOverlap());
        gc.setFill(backgroundColor);
        gc.fillRect((double) x * cell, (double) y * cell, cell, cell);
    }

    /**
     * Gets how many screen pixels a cell occupies. For overlapping sets a tile
     * is as big as its pattern, so the cell is one pattern pixel.
     */
    private int cellPixels(TileSet set, boolean overlap) {
        int scale = overlap ? OVERLAP_SCALE : set.getTileSize();
        return pixelSize * scale;
    }

    /**
     * Listener of one drawing session. It only accepts the operations of the
     * generation it was created for; it is deliberately lightweight, because a
     * new one is created on every restart.
     */
    private final class Session implements WfcSolver.BoardListener {

        private final long token;

        private Session(long token) {
            this.token = token;
        }

        @Override
        public void tilePlaced(int x, int y, int tileIndex, TileSet set) {
            pendingActions.add(new DrawTileAction(token, x, y, tileIndex, set));
        }

        @Override
        public void tileRemoved(int x, int y, int tileIndex, TileSet set) {
            pendingActions.add(new EraseTileAction(token, x, y, tileIndex, set));
        }
    }
}
