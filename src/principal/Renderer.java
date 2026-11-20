package principal;

import javafx.application.Platform;
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
 * carries the tile set it belongs to, and the tile index when it paints,
 * instead of looking them up when the action is finally painted.</p>
 *
 * <p>The queue is a {@link ConcurrentLinkedQueue} because it is filled from the
 * solver thread and drained from the JavaFX thread. Each generation draws
 * through its own {@linkplain #newSession(TileBoard, boolean) session}. Stopping
 * or replacing a session discards its pending actions and rejects any later
 * notifications from that generation. Session changes and frame updates are
 * performed on the JavaFX thread.</p>
 *
 * <p>A fast session skips the step-by-step drawing entirely: it ignores the
 * per-tile notifications and, when the solver reports the generation over,
 * paints the finished board in one go on the JavaFX thread.</p>
 */
final class Renderer {

    /**
     * A pending drawing operation.
     *
     * @param session   drawing session of the generation that made the change
     * @param x         column
     * @param y         row
     * @param tileIndex index of the tile to paint, or {@link TileBoard#EMPTY}
     *                  to clear the cell
     * @param set       tile set the index belongs to
     */
    private record TileAction(long session, int x, int y, int tileIndex, TileSet set) {

        /**
         * Performs the operation on the canvas.
         *
         * @param renderer renderer holding the graphics context
         */
        void execute(Renderer renderer) {
            if (tileIndex == TileBoard.EMPTY) {
                renderer.eraseTile(set, x, y);
            } else {
                renderer.paintTile(set, tileIndex, x, y);
            }
        }
    }

    private final GraphicsContext gc;
    private final Color backgroundColor;
    private final Queue<TileAction> pendingActions = new ConcurrentLinkedQueue<>();
    private final AtomicLong session = new AtomicLong();

    /**
     * Session of the generation in progress, kept so the user interface can
     * switch it to fast mode without starting a new generation. It is written
     * from the JavaFX thread only.
     */
    private Session currentSession;

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
     * @param fast  when {@code true} the session does not draw the growth step
     *              by step; it only paints the final board once the generation
     *              finishes
     * @return the listener the new generation has to report to
     */
    WfcSolver.BoardListener newSession(TileBoard board, boolean fast) {
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
        currentSession = new Session(token, board, fast);
        return currentSession;
    }

    /**
     * Switches the session of the generation in progress to fast mode, without
     * touching the generation itself. The whole pending drawing queue is
     * discarded and no new actions are enqueued; the tiles already painted stay
     * on the canvas until the generation ends, when the final board is painted
     * in one call. If the generation already ended by then, that final paint
     * happens right here instead. The session token is deliberately kept, so
     * the run is not invalidated. Does nothing when no session is active.
     */
    void switchToFast() {
        if (currentSession != null) {
            currentSession.switchToFast();
        }
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
    private void eraseTile(TileSet set, int x, int y) {
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
     * Paints every tile the board currently holds, in one call. Used by the
     * fast mode, where the growth was never drawn step by step.
     *
     * @param board board to paint
     * @param token session token the paint still belongs to
     */
    private void paintWholeBoard(TileBoard board, long token) {
        if (token != session.get()) {
            // A newer generation took over while the board was being computed.
            return;
        }
        TileSet set = board.getSet();
        for (int x = 0; x < board.getWidth(); x++) {
            for (int y = 0; y < board.getHeight(); y++) {
                int tileIndex = board.getTileIndex(x, y);
                if (tileIndex != TileBoard.EMPTY) {
                    paintTile(set, tileIndex, x, y);
                }
            }
        }
    }

    /**
     * Listener of one drawing session. It only accepts the operations of the
     * generation it was created for; it is deliberately lightweight, because a
     * new one is created on every restart. A fast listener drops the per-tile
     * notifications and paints the whole board when the generation ends. A
     * slow listener can also be switched to fast in the middle of its
     * generation (see {@link #switchToFast()}); the switch is one way, because
     * the session cannot resume step-by-step drawing over a canvas it stopped
     * watching.
     */
    private final class Session implements WfcSolver.BoardListener {

        private final long token;
        private final TileBoard board;
        private volatile boolean fast;
        /** Set by the solver thread once the generation is over. */
        private volatile boolean finished;

        private Session(long token, TileBoard board, boolean fast) {
            this.token = token;
            this.board = board;
            this.fast = fast;
        }

        /** Drops the step-by-step drawing of this session from now on. */
        private void switchToFast() {
            fast = true;
            // Same lock as enqueue: an action being added right now either
            // makes it into the cleared queue or is rejected afterwards.
            synchronized (pendingActions) {
                pendingActions.clear();
            }
            if (finished) {
                // The generation ended while this thread was still drawing it
                // step by step, so generationFinished() took the slow branch
                // and will never come back: paint the result now, or the
                // canvas would keep the half drawn state forever.
                paintWholeBoard(board, token);
            }
        }

        @Override
        public void tilePlaced(int x, int y, int tileIndex, TileSet set) {
            if (fast) {
                return;
            }
            enqueue(new TileAction(token, x, y, tileIndex, set));
        }

        @Override
        public void tileRemoved(int x, int y, TileSet set) {
            if (fast) {
                return;
            }
            enqueue(new TileAction(token, x, y, TileBoard.EMPTY, set));
        }

        @Override
        public void generationFinished() {
            // Written before checking fast, while switchToFast() writes fast
            // before checking finished: both cannot miss each other, so
            // either this call or the switch paints the whole board.
            finished = true;
            if (!fast) {
                return;
            }
            // Called from the solver thread; the canvas may only be painted
            // from the JavaFX thread.
            Platform.runLater(() -> paintWholeBoard(board, token));
        }
    }
}
