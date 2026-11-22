package principal;

import java.util.ArrayDeque;
import java.util.ArrayList;
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
 *       tries a different option from a previous decision, and</li>
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
         * @param x   column
         * @param y   row
         * @param set tile set of the board the tile was removed from
         */
        void tileRemoved(int x, int y, TileSet set);

        /**
         * Called once when the generation is over, whether the board was
         * completed or not. A run that was abandoned or replaced never calls
         * it, so listeners can safely assume they are still the current ones.
         */
        void generationFinished();
    }

    /** Maximum number of remembered decisions; older ones are discarded. */
    private static final int MAX_BACKTRACK_STATES = 300;
    /** Times the same empty cell may fail before its whole area is cleared. */
    private static final int STUCK_LIMIT = 50;
    /** Radius of the area cleared when a cell gets permanently stuck. */
    private static final int CLEAR_RADIUS = 4;
    /** Delay between steps, so the generation can be watched. */
    private static final int STEP_DELAY_MS = 2;

    private final ExecutorService executor;
    private final AtomicLong generation = new AtomicLong();
    private Future<?> running;
    private long seed;

    /**
     * Pacing flag of the generation in progress. It is volatile because the
     * user interface thread may raise it through {@link #setFast} while the
     * solver thread reads it on every step. It only gates the watching delay,
     * never a random choice, so toggling it in the middle of a generation
     * does not change the board it produces.
     */
    private volatile boolean fast;

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
     * Changes the pacing of the generation in progress without restarting it.
     * The solver notices on its next step: from there on it stops (or resumes)
     * the per-step watching delay, keeping the board, the backtracking history
     * and the random sequence untouched.
     *
     * @param fast when {@code true} the steps are no longer delayed
     */
    void setFast(boolean fast) {
        this.fast = fast;
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
     * @param fast     initial pacing for this generation; it can be changed
     *                 later, mid run, with {@link #setFast}
     * @throws IllegalArgumentException if the board or the listener is {@code null}
     */
    void solve(TileBoard board, BoardListener listener, boolean fast) {
        if (board == null) {
            throw new IllegalArgumentException("Board cannot be null");
        }
        if (listener == null) {
            throw new IllegalArgumentException("Listener cannot be null");
        }
        long runId = generation.incrementAndGet();
        this.fast = fast;
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
        } else {
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
        if (!isStale(runId)) {
            listener.generationFinished();
        }
    }

    /**
     * Seeds the board. If the set starts from bottom, the entire bottom row
     * is filled with bottom tiles, growing from the bottom-left corner. For any
     * other set the floor rules do not apply, so a single random tile is placed
     * in the centre.
     */
    private void placeInitialTile(TileBoard board, Random random) {
        TileSet set = board.getSet();
        boolean startsFromBottom = set.startsFromBottom();

        if (startsFromBottom) {
            // Seed the entire bottom row for 'fromBottom' sets
            int bottomRow = board.getHeight() - 1;
            for (int x = 0; x < board.getWidth(); x++) {
                int tileIndex = set.randomBottomTileIndex(random);
                board.place(x, bottomRow, tileIndex);
                listener.tilePlaced(x, bottomRow, tileIndex, set);
            }
        } else {
            // Standard seeding: one tile in the middle
            int tileIndex = set.randomTileIndex(random);
            TileBoard.Point at = board.getMiddle();
            board.place(at.x(), at.y(), tileIndex);
            listener.tilePlaced(at.x(), at.y(), tileIndex, set);
        }
    }

    /**
     * Runs the main loop.
     *
     * @return {@code true} if the board was completed, {@code false} if the
     *         history was exhausted or the run was abandoned
     */
    private boolean collapse(TileBoard board, Random random, long runId) {
        Deque<TileBoard.Options> stack = new ArrayDeque<>();
        Map<TileBoard.Point, Integer> stuckCounts = new HashMap<>();

        while (!isStale(runId)) {
            TileBoard.Point anchor = board.getLowestEntropyAnchor(random);
            if (anchor == null) {
                return board.isComplete();
            }

            TileBoard.Options options = board.newOptions(anchor.x(), anchor.y());

            if (!options.pickOption(random)) {
                resolveContradiction(board, anchor, stuckCounts, random);
                if (!tryAnotherOption(board, stack, random)) {
                    return false;
                }
            } else {
                place(board, options);

                if (stack.size() >= MAX_BACKTRACK_STATES) {
                    stack.pollFirst();
                }
                stack.addLast(options);
            }

            if (!fast) {
                try {
                    Thread.sleep(STEP_DELAY_MS);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    return false;
                }
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
        // Snapshot the stuck cells first: the repairs below empty cells too,
        // and only the neighbours empty at this moment may be handled.
        List<TileBoard.Point> emptyCells = new ArrayList<>();
        board.forEachNeighbor(anchor.x(), anchor.y(), (empty, x, y) -> {
            if (empty) {
                emptyCells.add(new TileBoard.Point(x, y));
            }
        });
        for (TileBoard.Point cell : emptyCells) {
            int times = stuckCounts.getOrDefault(cell, 0) + 1;
            if (times >= STUCK_LIMIT) {
                stuckCounts.remove(cell);
                clearArea(board, cell);
            } else {
                stuckCounts.put(cell, times);
                clearRandomNeighbor(board, cell, random);
            }
        }
    }

    private void clearRandomNeighbor(TileBoard board, TileBoard.Point around, Random random) {
        TileBoard.Point victim = board.randomOccupiedNeighbor(around.x(), around.y(), random);
        if (victim != null) {
            clearTile(board, victim.x(), victim.y());
        }
    }

    private void clearArea(TileBoard board, TileBoard.Point center) {
        for (int dy = -CLEAR_RADIUS; dy <= CLEAR_RADIUS; dy++) {
            for (int dx = -CLEAR_RADIUS; dx <= CLEAR_RADIUS; dx++) {
                clearTile(board, center.x() + dx, center.y() + dy);
            }
        }
    }

    /**
     * Undoes the most recent decisions until one of them has an untried option
     * left, and places that option.
     *
     * <p>The picker reads the live domains, so it only ever offers a tile that
     * fits the current neighbours into a cell that is empty right now: there is
     * no stale option to skip and no risk of overwriting work done elsewhere.
     * The tried list keeps the decision from retrying the option that just
     * failed.</p>
     *
     * @param random source used by the picker to choose among untried options
     * @return {@code true} if a decision handed out another option,
     *         {@code false} if the history is exhausted (no solution from this
     *         state)
     */
    private boolean tryAnotherOption(TileBoard board, Deque<TileBoard.Options> stack, Random random) {
        while (!stack.isEmpty()) {
            TileBoard.Options options = stack.pollLast();
            // Undo the placement this decision made last, if it survived there.
            clearTile(board, options.x(), options.y());

            if (options.pickOption(random)) {
                place(board, options);
                stack.addLast(options);
                return true;
            }
        }
        return false;
    }

    private void place(TileBoard board, TileBoard.Options options) {
        board.place(options.x(), options.y(), options.tileIndex());
        listener.tilePlaced(options.x(), options.y(), options.tileIndex(), board.getSet());
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
        listener.tileRemoved(x, y, board.getSet());
    }
}
