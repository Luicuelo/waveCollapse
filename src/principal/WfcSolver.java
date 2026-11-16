package principal;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Grows a {@link TileBoard} by placing tiles that are compatible with their
 * neighbours, and backtracks when it paints itself into a corner.
 *
 * <p>Each step:</p>
 *
 * <ol>
 *   <li>finds the placed tile with the fewest legal continuations (the "lowest
 *       entropy" anchor),</li>
 *   <li>collects the tiles that may be placed in its empty neighbours,</li>
 *   <li>if there are none, breaks the deadlock by clearing a neighbour (and,
 *       when the same spot keeps failing, a whole area around it) and then
 *       replays a previous decision from the state stack, and</li>
 *   <li>otherwise places one of the candidates and remembers the decision so it
 *       can be undone later.</li>
 * </ol>
 *
 * <p>The generation always runs on a single background thread owned by this
 * class, so the JavaFX thread is never blocked and two generations can never
 * write to a board at the same time. Starting a new generation simply marks the
 * previous one as stale (a monotonically increasing run id) and lets it die;
 * the executor guarantees the new run only starts once the old one has
 * finished.</p>
 */
final class WfcSolver {

    /**
     * Receives the changes made to the board so they can be drawn.
     *
     * <p>A listener belongs to one generation: the drawing side hands a fresh
     * one to every run, so it can tell its own notifications from the ones a
     * previous, abandoned run is still sending.</p>
     */
    interface BoardListener {

        /**
         * Called after a tile has been placed.
         *
         * @param x         column
         * @param y         row
         * @param tileIndex index of the placed tile in the set
         * @param set       tile set the index belongs to
         */
        void tilePlaced(int x, int y, int tileIndex, TileSet set);

        /**
         * Called after a tile has been removed.
         *
         * @param x         column
         * @param y         row
         * @param tileIndex index the removed tile had in the set
         * @param set       tile set the index belongs to
         */
        void tileRemoved(int x, int y, int tileIndex, TileSet set);
    }

    /** Maximum number of remembered decisions; older ones are discarded. */
    private static final int MAX_BACKTRACK_STATES = 300;
    /** Times the same empty cell may fail before its whole area is cleared. */
    private static final int STUCK_LIMIT = 10;
    /** Radius of the area cleared when a cell gets permanently stuck. */
    private static final int CLEAR_RADIUS = 4;
    /** Delay between steps, so the generation can be watched. */
    private static final int STEP_DELAY_MS = 2;

    private final ExecutorService executor;
    private final AtomicLong generation = new AtomicLong();
    private Future<?> running;
    private long seed;

    /**
     * Listener of the generation in progress. It is assigned at the beginning
     * of a run and only read by the solver thread, so no other thread has to
     * synchronize on it.
     */
    private BoardListener listener;

    /**
     * Creates a solver with its own generation thread. Every run is told which
     * listener has to receive its progress.
     */
    WfcSolver() {
        this.executor = Executors.newSingleThreadExecutor(task -> {
            Thread thread = new Thread(task, "wfc-solver");
            thread.setDaemon(true);
            return thread;
        });
    }

    /**
     * Sets the seed used by the next generation. Using the same seed twice
     * produces the same board, which makes a pattern reproducible.
     *
     * @param seed the seed of the next generation
     */
    void setSeed(long seed) {
        this.seed = seed;
    }

    /**
     * Starts a new generation on the given board. The call returns immediately;
     * any generation still in progress is abandoned first.
     *
     * <p>The board becomes the property of the new run: it is read and written
     * only by the solver thread. A board is meant to be used once, by the single
     * generation it was built for; a previous run that is still dying out keeps
     * writing to its own, older board.</p>
     *
     * @param board    the board to fill
     * @param listener listener that receives the progress of this run only
     * @throws IllegalArgumentException if the board or the listener is {@code null}
     */
    void solve(TileBoard board, BoardListener listener) {
        if (board == null) {
            throw new IllegalArgumentException("Board cannot be null");
        }
        if (listener == null) {
            throw new IllegalArgumentException("Listener cannot be null");
        }
        long runId = generation.incrementAndGet();
        cancelRunning();
        running = executor.submit(() -> runGeneration(board, listener, runId));
    }

    /**
     * Abandons the generation in progress, if any. Non blocking: the solver
     * notices on its next step.
     */
    void stop() {
        generation.incrementAndGet();
        cancelRunning();
    }

    /**
     * Stops the solver for good and releases its thread.
     */
    void shutdown() {
        stop();
        executor.shutdownNow();
    }

    private void cancelRunning() {
        if (running != null) {
            running.cancel(true);
        }
    }

    /**
     * Tells whether the given run has been superseded or interrupted.
     */
    private boolean isStale(long runId) {
        return runId != generation.get() || Thread.currentThread().isInterrupted();
    }

    private void runGeneration(TileBoard board, BoardListener listener, long runId) {
        this.listener = listener;
        TileSet set = board.getSet();
        if (set.size() == 0) {
            System.err.println("No tiles available for " + set);
            return;
        }

        Random random = new Random(seed);
        try {
            // Everything is inside the try block: if anything fails, the
            // executor simply moves on to the next generation instead of
            // leaving the solver in a half dead state.
            placeInitialTile(board, random);
            boolean collapsed = collapse(board, random, runId);
            if (!isStale(runId)) {
                System.out.println(collapsed
                        ? "Generation finished: board collapsed."
                        : "Generation finished: no solution found.");
            }
        } catch (RuntimeException e) {
            System.err.println("Generation aborted: " + e);
        }
    }

    /**
     * Seeds the board with a single tile: a bottom tile for sets that need it,
     * a random one in the centre of the board otherwise.
     */
    private void placeInitialTile(TileBoard board, Random random) {
        TileSet set = board.getSet();
        boolean startsFromBottom = set.startsFromBottom();
        int tileIndex = startsFromBottom
                ? set.randomBottomTileIndex(random)
                : set.randomTileIndex(random);
        TileBoard.Point at = startsFromBottom
                ? new TileBoard.Point(0, board.getHeight() - 1)
                : board.getMiddle();

        board.place(at.x(), at.y(), tileIndex);
        listener.tilePlaced(at.x(), at.y(), tileIndex, set);
    }

    /**
     * Runs the main loop.
     *
     * @return {@code true} if the board was completed, {@code false} if the
     *         history was exhausted or the run was abandoned
     */
    private boolean collapse(TileBoard board, Random random, long runId) {
        Deque<CollapseState> stack = new ArrayDeque<>();
        Map<TileBoard.Point, Integer> stuckCounts = new HashMap<>();

        while (!isStale(runId)) {
            TileBoard.Point anchor = board.getLowestEntropyAnchor(random);
            if (anchor == null) {
                return board.isComplete();
            }

            List<TileBoard.Placement> options =
                    board.getPossiblePlacements(anchor.x(), anchor.y(), random);

            if (options.isEmpty()) {
                resolveContradiction(board, anchor, stuckCounts, random);
                if (!replayLastDecision(board, stack)) {
                    return false;
                }
            } else {
                CollapseState state = new CollapseState(options);
                TileBoard.Placement first = state.nextPlacement();
                place(board, first);
                state.markPlaced(first.x(), first.y());

                if (stack.size() >= MAX_BACKTRACK_STATES) {
                    stack.pollFirst();
                }
                stack.addLast(state);
            }

            try {
                Thread.sleep(STEP_DELAY_MS);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return false;
            }
        }
        return false;
    }

    /**
     * Breaks a deadlock: every empty neighbour of the stuck anchor loses one of
     * its tiles, and cells that keep failing lose their whole surroundings.
     */
    private void resolveContradiction(TileBoard board, TileBoard.Point anchor,
                                      Map<TileBoard.Point, Integer> stuckCounts, Random random) {
        for (TileBoard.Point empty : board.getEmptyNeighbors(anchor.x(), anchor.y())) {
            int times = stuckCounts.merge(empty, 1, Integer::sum);
            if (times >= STUCK_LIMIT) {
                stuckCounts.put(empty, 0);
                clearArea(board, empty);
            } else {
                clearRandomNeighbor(board, empty, random);
            }
        }
    }

    private void clearRandomNeighbor(TileBoard board, TileBoard.Point around, Random random) {
        List<TileBoard.Point> occupied = board.getOccupiedNeighbors(around.x(), around.y());
        if (occupied.isEmpty()) {
            return;
        }
        TileBoard.Point victim = occupied.get(random.nextInt(occupied.size()));
        clearTile(board, victim.x(), victim.y());
    }

    private void clearArea(TileBoard board, TileBoard.Point center) {
        for (int dy = -CLEAR_RADIUS; dy <= CLEAR_RADIUS; dy++) {
            for (int dx = -CLEAR_RADIUS; dx <= CLEAR_RADIUS; dx++) {
                clearTile(board, center.x() + dx, center.y() + dy);
            }
        }
    }

    /**
     * Undoes the most recent decisions until one of them has an option left, and
     * places that option.
     *
     * <p>An option whose cell was filled again by older work is skipped instead
     * of overwriting it, so a backtrack never destroys a tile it does not own.</p>
     *
     * @return {@code true} if a decision could be replayed, {@code false} if the
     *         history is exhausted (no solution from this state)
     */
    private boolean replayLastDecision(TileBoard board, Deque<CollapseState> stack) {
        while (!stack.isEmpty()) {
            CollapseState state = stack.pollLast();
            if (state.hasPlacedTile()) {
                clearTile(board, state.placedX(), state.placedY());
            }

            TileBoard.Placement option;
            while ((option = state.nextPlacement()) != null) {
                if (!board.isEmpty(option.x(), option.y())) {
                    continue;
                }
                place(board, option);
                state.markPlaced(option.x(), option.y());
                stack.addLast(state);
                return true;
            }
        }
        return false;
    }

    private void place(TileBoard board, TileBoard.Placement placement) {
        board.place(placement.x(), placement.y(), placement.tileIndex());
        listener.tilePlaced(placement.x(), placement.y(), placement.tileIndex(), board.getSet());
    }

    private void clearTile(TileBoard board, int x, int y) {
        if (!board.inBounds(x, y)) {
            return;
        }
        int tileIndex = board.getTileIndex(x, y);
        if (tileIndex == TileBoard.EMPTY) {
            return;
        }
        board.clear(x, y);
        listener.tileRemoved(x, y, tileIndex, board.getSet());
    }

    /**
     * A decision taken by the solver: the candidate placements that were
     * available and the cell that was finally filled.
     */
    private static final class CollapseState {

        private final List<TileBoard.Placement> options;
        private int nextIndex;
        private int placedX = -1;
        private int placedY = -1;

        CollapseState(List<TileBoard.Placement> options) {
            this.options = options;
        }

        /**
         * Takes the next untried placement.
         *
         * @return the placement, or {@code null} when the state is exhausted
         */
        TileBoard.Placement nextPlacement() {
            return nextIndex < options.size() ? options.get(nextIndex++) : null;
        }

        void markPlaced(int x, int y) {
            this.placedX = x;
            this.placedY = y;
        }

        boolean hasPlacedTile() {
            return placedX >= 0 && placedY >= 0;
        }

        int placedX() {
            return placedX;
        }

        int placedY() {
            return placedY;
        }
    }
}
