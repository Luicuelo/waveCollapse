package principal;

import javafx.scene.canvas.GraphicsContext;
import javafx.scene.image.Image;
import javafx.scene.paint.Color;

import java.util.Arrays;
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
 * through its own {@linkplain #newSession(TileBoard) session}. Stopping or
 * replacing a session discards its pending actions and rejects any later
 * notifications from that generation. Session changes and frame updates are
 * performed on the JavaFX thread.</p>
 */
final class Renderer {

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
    private int[][] displayedTiles;
    private int overlapOffsetY;

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
     * notifications from a previous generation are rejected.</p>
     *
     * @param board board whose drawing sequence will be displayed
     * @return the listener the new generation has to report to
     */
    WfcSolver.BoardListener newSession(TileBoard board) {
        long token = resetPendingActions();
        displayedTiles = null;
        overlapOffsetY = 0;
        TileSet set = board.getSet();
        if (set.usesOverlap()) {
            displayedTiles = new int[board.getWidth()][board.getHeight()];
            for (int[] column : displayedTiles) {
                Arrays.fill(column, TileBoard.EMPTY);
            }
            int cell = cellPixels(set);
            int imageSize = set.getTileSize() * tilePixelSize(set);
            overlapOffsetY = (int) gc.getCanvas().getHeight() - (board.getHeight() - 1) * cell - imageSize;
        }
        return new Session(token);
    }

    /**
     * Discards pending actions and prevents the stopped session from adding more.
     * Already painted tiles remain on the canvas.
     */
    void stopSession() {
        resetPendingActions();
    }

    private long resetPendingActions() {
        synchronized (pendingActions) {
            long token = session.incrementAndGet();
            pendingActions.clear();
            return token;
        }
    }

    private void enqueue(TileAction action) {
        // Check and insert atomically with session invalidation and queue clearing.
        synchronized (pendingActions) {
            if (action.session() == session.get()) {
                pendingActions.add(action);
            }
        }
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

        if (set.usesOverlap()) {
            displayedTiles[x][y] = tileIndex;
            redrawOverlap(set, x, y);
            return;
        }

        int cell = cellPixels(set);
        gc.setImageSmoothing(false);
        gc.drawImage(image, (double) x * cell, (double) y * cell,
                image.getWidth() * pixelSize, image.getHeight() * pixelSize);
    }

    /**
     * Clears the area covered by a tile with the background colour.
     */
    private void eraseTile(TileSet set, int tileIndex, int x, int y) {
        if (set.get(tileIndex) == null) {
            return;
        }
        if (set.usesOverlap()) {
            displayedTiles[x][y] = TileBoard.EMPTY;
            redrawOverlap(set, x, y);
            return;
        }
        int cell = cellPixels(set);
        gc.setFill(backgroundColor);
        gc.fillRect((double) x * cell, (double) y * cell, cell, cell);
    }

    /**
     * Reconstructs the affected area from the tiles already drawn. Tiles above
     * and to the right are drawn last and take precedence in the overlap.
     */
    private void redrawOverlap(TileSet set, int x, int y) {
        int cell = cellPixels(set);
        int zoom = tilePixelSize(set);
        int imageSize = set.getTileSize() * zoom;
        double screenX = (double) x * cell;
        double screenY = (double) y * cell + overlapOffsetY;

        gc.save();
        gc.beginPath();
        gc.rect(screenX, screenY, imageSize, imageSize);
        gc.clip();
        gc.setFill(backgroundColor);
        gc.fillRect(screenX, screenY, imageSize, imageSize);
        gc.setImageSmoothing(false);
        for (int row = y + 1; row >= y - 1; row--) {
            for (int column = x - 1; column <= x + 1; column++) {
                if (column < 0 || column >= displayedTiles.length
                        || row < 0 || row >= displayedTiles[column].length) {
                    continue;
                }
                Tile tile = set.get(displayedTiles[column][row]);
                if (tile != null && tile.getImage() != null) {
                    Image image = tile.getImage();
                    gc.drawImage(image, (double) column * cell, (double) row * cell + overlapOffsetY,
                            image.getWidth() * zoom, image.getHeight() * zoom);
                }
            }
        }
        gc.restore();
    }

    /** Distance between anchors in screen pixels. */
    private int cellPixels(TileSet set) {
        return pixelSize * set.getCellSize();
    }

    /** Size of a pattern pixel on screen. */
    private int tilePixelSize(TileSet set) {
        return pixelSize * (set.usesOverlap() ? set.getTileSize() : 1);
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
            enqueue(new DrawTileAction(token, x, y, tileIndex, set));
        }

        @Override
        public void tileRemoved(int x, int y, int tileIndex, TileSet set) {
            enqueue(new EraseTileAction(token, x, y, tileIndex, set));
        }
    }
}
