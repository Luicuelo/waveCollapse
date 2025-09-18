package principal;

import javafx.animation.AnimationTimer;
import javafx.application.Application;
import javafx.application.Platform;
import javafx.geometry.Insets;
import javafx.scene.Scene;
import javafx.scene.canvas.Canvas;
import javafx.scene.canvas.GraphicsContext;
import javafx.scene.control.Button;
import javafx.scene.control.ComboBox;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.HBox;
import javafx.scene.paint.Color;
import javafx.stage.Stage;
import principal.TitlesManager.PointInt;

import java.util.ArrayDeque;
import java.util.List;


/**
 * Wave Function Collapse Visualization Application
 * <p>
 * A JavaFX application that visualizes the Wave Function Collapse algorithm in
 * real-time.
 * Features:
 * - Interactive tile visualization on a grid-based canvas
 * - Step-by-step execution control (restart, iterate, stop, step)
 * - Configurable canvas size via command line arguments
 * - Real-time animation of tile placement operations
 * </p>
 */
public class WaveFunction extends Application {

    /**
     * The size multiplier for each pixel in the visualization
     */
    protected static int pixel_size = 4;
    /**
     * The default width of the canvas in pixels
     */
    private static final int DEFAULT_WIDTH = 800;
    /**
     * The default height of the canvas in pixels
     */
    private static final int DEFAULT_HEIGHT = 800;
    /**
     * The height of the control buttons in pixels
     */
    private static final int BUTTON_HEIGHT = 25;
    /**
     * The background color for the canvas
     */
    private static final Color BACKGROUND_COLOR = Color.LIGHTGRAY;
    /**
     * Number of frames to skip between updates for performance
     */
    private static final int FRAME_SKIP = 1; // Update every FRAME_SKIP frames for performance
    /**
     * The actual width of the canvas in pixels
     */
    private int canvasWidth;
    /**
     * The actual height of the canvas in pixels
     */
    private int canvasHeight;
    /**
     * The canvas for drawing the visualization
     */
    private Canvas canvas;
    /**
     * The renderer responsible for drawing the visualization
     */
    private Renderer renderer;
    /**
     * The animation timer for updating the visualization
     */
    private AnimationTimer animationTimer;
    /**
     * Flag indicating if the animation has finished
     */
    private boolean finished = false;
    /**
     * Frame counter for controlling animation speed
     */
    private double frameCounter = 0;
    /**
     * Flag indicating if the collapse algorithm is currently running
     */
    private volatile boolean collapseRunning = false;
    /**
     * Flag to signal the collapse algorithm to stop
     */
    private volatile boolean shouldStopCollapse = false;
    /**
     * Thread for running the collapse algorithm
     */
    private Thread collapseThread = null;
    /**
     * The current tile set being used
     */
    private TitlesManager.TitleSet actualSet=TitlesManager.TitleSet.CIRCUIT;
    /**
     * Main method to launch the JavaFX application.
     *
     * @param args command line arguments
     */
    public static void main(String[] args) {
        launch(args);
    }

    /**
     * Starts the JavaFX application and initializes the user interface.
     * Creates the main window with canvas for visualization and control buttons.
     *
     * @param primaryStage the primary stage for this application
     */
    @Override
    public void start(Stage primaryStage) {

       
        // Parse command line arguments for size
        Parameters params = getParameters();
        parseSize(params);

        primaryStage.setTitle("Wave Function Collapse");

        // Create and initialize
        initialize();

        // Setup mouse listeners
        // setupMouseListeners();

        // Create the restart button
        Button restartButton = new Button("Restart");
        restartButton.setOnAction(e -> restart());
        restartButton.setPrefHeight(BUTTON_HEIGHT);

        // Create the Stop button to pause animation
        Button stopButton = new Button("Stop");
        stopButton.setOnAction(e -> stopIteration());
        stopButton.setPrefHeight(BUTTON_HEIGHT);

		// Create the Set selector dropdown
		ComboBox<TitlesManager.TitleSet> setSelector = new ComboBox<>();
		setSelector.getItems().addAll(TitlesManager.TitleSet.values());
		setSelector.setValue(TitlesManager.TitleSet.CIRCUIT);
		setSelector.setPrefHeight(BUTTON_HEIGHT);
		// Add a listener to update the actualSet field when selection changes
		setSelector.setOnAction(e -> {
            stopIteration();
			actualSet = setSelector.getValue();
            TitlesManager.loadTitles(actualSet);
			restart();
		});

        Integer[] pixelSizes={1,2,3,4,5,6,7,8};
        ComboBox<Integer> pixelSizeSelector = new ComboBox<>();
        pixelSizeSelector.getItems().addAll(pixelSizes);
		pixelSizeSelector.setValue(4);
        pixelSizeSelector.setPrefHeight(BUTTON_HEIGHT);
		// Add a listener to update the pixel_size field when selection changes
		pixelSizeSelector.setOnAction(e -> {
            stopIteration();
			pixel_size=pixelSizeSelector.getValue();
            renderer.PIXEL_SIZE=pixel_size;
			restart();
		});

        // Layout
        BorderPane root = new BorderPane();
        root.setCenter(canvas);


        HBox buttons = new HBox();
        root.setBottom(buttons);
        buttons.setPadding(new Insets(0, 10, 0, 0));
        buttons.setSpacing(10);
        buttons.getChildren().addAll(restartButton, stopButton,setSelector,pixelSizeSelector);

        // Create scene and show
        Scene scene = new Scene(root, canvasWidth, canvasHeight + BUTTON_HEIGHT);
        primaryStage.setScene(scene);
        primaryStage.setResizable(false);
        primaryStage.show();
        renderer.iterate();

    }

    /**
     * Tests tile placement by arranging all available tiles on the board.
     * This method is used for visualization and testing purposes.
     */
    private void testTitles() {
        int x = 0;
        int y = 0;
        String lastname = "";

        for (int a = 0; a < TitlesManager.getTitlesCount(); a++) {
            String name = TitlesManager.getTitleName(a);

            if (x >= TitlesManager.TitleBoard.getBoardWidth() || (x > 0 && !name.equals(lastname))) {
                x = 0;
                y++;
                if (y >= TitlesManager.TitleBoard.getBoardHeight())
                    break; // Prevent overflow
            }
            lastname = name;
            TitlesManager.TitleBoard.setTitleAtPos(x, y, a);
            x++;
        }
    }

    /**
     * Parses command line arguments for canvas size.
     *
     * @param params the parameters to parse
     */
    private void parseSize(Parameters params) {
        try {
            if (params.getNamed().containsKey("width")) {
                canvasWidth = Integer.parseInt(params.getNamed().get("width"));
            } else {
                canvasWidth = DEFAULT_WIDTH;
            }

            if (params.getNamed().containsKey("height")) {
                canvasHeight = Integer.parseInt(params.getNamed().get("height"));
            } else {
                canvasHeight = DEFAULT_HEIGHT;
            }
        } catch (NumberFormatException e) {
            System.err.println("Invalid size parameters, using defaults");
            canvasWidth = DEFAULT_WIDTH;
            canvasHeight = DEFAULT_HEIGHT;
        }
    }

    /**
     * Initializes the canvas and starts the animation loop.
     * Sets up the graphics context, creates the renderer, and begins the animation
     * timer for real-time visualization updates.
     */
    private void initialize() {
        canvas = new Canvas(canvasWidth, canvasHeight);

        GraphicsContext gc = canvas.getGraphicsContext2D();

        gc.clearRect(0, 0, canvasWidth, canvasHeight);
        gc.setFill(BACKGROUND_COLOR);
        gc.fillRect(0, 0, canvasWidth, canvasHeight);

        finished = false;

        renderer = new Renderer(gc, canvasWidth, canvasHeight, pixel_size, BACKGROUND_COLOR);
        animationTimer = new AnimationTimer() {
            @Override
            public void handle(long now) {
                if (!finished && renderer != null) {
                    frameCounter++;
                    if (frameCounter >= Double.MAX_VALUE)
                        frameCounter = 0;
                    if (frameCounter % FRAME_SKIP == 0) {
                        try {
                            renderer.updateFrame();
                        } catch (Exception e) {
                            System.err.println("Error updating : " + e.getMessage());
                            stopAnimation();
                        }
                    }
                }
            }
        };


        renderer.iterate();
        animationTimer.start();
    }

    /**
     * Represents the state of a collapse operation for backtracking purposes.
     * Stores information about possible tile placements and the current state of the algorithm.
     */
    private static class CollapseState {
        /**
         * The best position to place a tile
         */
        protected TitlesManager.PointInt bestTitlePosition;
        /**
         * List of possible tiles and their directions for placement
         */
        protected List<TitlesManager.TitleBoard.TitleAndDirection> possibleTitles;
        private int actualIndex;
        private int placedX = -1, placedY = -1;

        /**
         * Constructs a new CollapseState with the specified parameters
         *
         * @param bestTitlePosition the best position to place a tile
         * @param possibleTitles list of possible tiles and their directions
         */
        public CollapseState(TitlesManager.PointInt bestTitlePosition,
                List<TitlesManager.TitleBoard.TitleAndDirection> possibleTitles) {
            this.bestTitlePosition = bestTitlePosition;
            this.possibleTitles = possibleTitles;
            this.actualIndex = 0;
        }
        
        /**
         * Gets the next tile from the list of possible tiles
         *
         * @return the next tile and its direction, or null if no more tiles
         */
        public TitlesManager.TitleBoard.TitleAndDirection getNextTitle() {
            if (actualIndex >= possibleTitles.size())
                return null;
            return possibleTitles.get(actualIndex++);
        }
        
        /**
         * Sets the position where a tile was placed
         *
         * @param x the x-coordinate where the tile was placed
         * @param y the y-coordinate where the tile was placed
         */
        public void setPlacedPosition(int x, int y) {
            this.placedX = x;
            this.placedY = y;
        }

        /**
         * Checks if a tile has been placed
         *
         * @return true if a tile has been placed, false otherwise
         */
        public boolean hasPlacedTile() {
            return placedX != -1 && placedY != -1;
        }
    }

    /**
     * Begins the Wave Function Collapse algorithm.
     * <p>
     * This method implements the core of the Wave Function Collapse algorithm with the following key steps:
     * 1. Find the position with the least possible tile options (lowest entropy)
     * 2. Get all possible tiles that can be placed at that position
     * 3. If there are no possible tiles:
     *    - Find an impossible position near the problematic cell
     *    - Remove a random adjacent tile to break the deadlock
     *    - Backtrack using a limited-size stack of previous states
     * 4. If there are possible tiles:
     *    - Create a new state with the possible tiles
     *    - Place the first possible tile
     *    - Add the state to the stack for potential backtracking
     * 5. Continue until the board is complete or no solution is found
     * </p>
     *
     * @return true if the algorithm successfully completed, false if it failed or was interrupted
     */
    private boolean beginCollapse() {
        ArrayDeque<CollapseState> stack = new ArrayDeque<>();
        final int MAX_STACK_SIZE = 100;

        while (!shouldStopCollapse) {
            TitlesManager.PointInt bestTitlePosition = TitlesManager.TitleBoard.getPosWithLessPossibleTitle();
            if (bestTitlePosition == null) {
                System.out.println("Collapsed - Complete");
                return true;
            }

            List<TitlesManager.TitleBoard.TitleAndDirection> possibleTitles = TitlesManager.TitleBoard
                    .getPossibleTitles(bestTitlePosition.x(), bestTitlePosition.y());

            //No possible tiles, we should Backtrack,
            if (possibleTitles.isEmpty()) {

                //first, find an impossible position near the problematic cell and removes random cell to break the deadlock
                TitlesManager.PointInt impossiblePos = TitlesManager.TitleBoard.findImpossiblePositionNear(
                        bestTitlePosition.x(), bestTitlePosition.y());
                if (impossiblePos != null) {
                    TitlesManager.PointInt pointToRemove = TitlesManager.TitleBoard.getRandomTileToRemove(
                            impossiblePos.x(), impossiblePos.y());
                    TitlesManager.TitleBoard.delTitleAtPos(pointToRemove.x(), pointToRemove.y());
                    Renderer.actionQueue.add(new Renderer.Action(Renderer.Action.Operations.DEL_Title,
                            pointToRemove.x(), pointToRemove.y()));
                }

                while (!stack.isEmpty()) {
                    CollapseState state = stack.pollLast(); //pop

                    if (state.hasPlacedTile()) {
                        TitlesManager.TitleBoard.delTitleAtPos(state.placedX, state.placedY);
                        Renderer.actionQueue.add(new Renderer.Action(
                                Renderer.Action.Operations.DEL_Title, state.placedX, state.placedY));
                    }
                    TitlesManager.TitleBoard.TitleAndDirection nextOption = state.getNextTitle();
                    if (nextOption != null) {
                        TitlesManager.PointInt nextTitlePos =nextOption.getPositionFromDirection(state.bestTitlePosition);
                        TitlesManager.TitleBoard.setTitleAtPos(nextTitlePos.x(), nextTitlePos.y(), nextOption.getTitle());
                        Renderer.actionQueue.add(new Renderer.Action(
                                Renderer.Action.Operations.ADD_Title, nextTitlePos.x(), nextTitlePos.y()));
                        state.setPlacedPosition(nextTitlePos.x(), nextTitlePos.y());
                        stack.addLast(state); // push to the end

                        break;
                    }
                }

                if (stack.isEmpty()) {
                    System.out.println("Failed - No solution");
                    return false;
                }

                // After backtracking, restart the main loop to re-evaluate the best position
                // with the updated board state

            }
            else

            {  // Normal Case, we have possible tiles

                // New State
                CollapseState newState = new CollapseState(bestTitlePosition, possibleTitles);
                TitlesManager.TitleBoard.TitleAndDirection firstOption = newState.getNextTitle();

                assert firstOption != null;
                TitlesManager.PointInt firstTitlePos = firstOption.getPositionFromDirection(bestTitlePosition);
                TitlesManager.TitleBoard.setTitleAtPos(firstTitlePos.x(), firstTitlePos.y(), firstOption.getTitle());
                Renderer.actionQueue.add(new Renderer.Action(
                        Renderer.Action.Operations.ADD_Title, firstTitlePos.x(), firstTitlePos.y()));
                newState.setPlacedPosition(firstTitlePos.x(), firstTitlePos.y());

                // Limit stack size
                if (stack.size() >= MAX_STACK_SIZE) {
                    stack.pollFirst(); // remove from the beginning
                }
                stack.addLast(newState); // push to the end

                // Check completeness
                if (TitlesManager.TitleBoard.getNumberOfTitles() == TitlesManager.TitleBoard.getBoardWidth()
                        * TitlesManager.TitleBoard.getBoardHeight()) {
                    System.out.println("Collapsed - Board complete");
                    return true;
                }
            }
            try {
                Thread.sleep(5);
            } catch (InterruptedException e) {
                return false;
            }
        }

        return false;
    }


    /**
     * Restarts the visualization with a new arrangement of tiles.
     * Clears the current visualization and starts fresh.
     */
    private void restart() {
        if (renderer == null)
            return;

        System.out.println("Restarting...");

        TitlesManager.loadTitles(actualSet);
        int titleSize=(actualSet.getSize());
        TitlesManager.TitleBoard.initializeBoard(canvasWidth / (titleSize * pixel_size),
                canvasHeight / (titleSize * pixel_size));

        shouldStopCollapse = true;

        while (collapseRunning) {
            try {
                Thread.sleep(10);
            } catch (InterruptedException e) {
                break;
            }
        }

        TitlesManager.TitleBoard.initializeBoard(
                TitlesManager.TitleBoard.getBoardWidth(),
                TitlesManager.TitleBoard.getBoardHeight());

        // Clear canvas
        GraphicsContext gc = canvas.getGraphicsContext2D();
        gc.clearRect(0, 0, canvasWidth, canvasHeight);
        gc.setFill(BACKGROUND_COLOR);
        gc.fillRect(0, 0, canvasWidth, canvasHeight);

        renderer.restart();
        PointInt middle = TitlesManager.TitleBoard.getMiddlePoint();
        int TitleIndex = TitlesManager.TitleBoard.getRandomTitleIndex();

        TitlesManager.TitleBoard.setTitleAtPos(middle.x(), middle.y(), TitleIndex);
        Renderer.actionQueue.add(new Renderer.Action(Renderer.Action.Operations.ADD_Title, middle.x(), middle.y()));

        shouldStopCollapse = false;

        collapseThread = new Thread(() -> {
            try {
                Thread.sleep(100);
                collapseRunning = true;
                beginCollapse();
                collapseRunning = false;
            } catch (InterruptedException e) {
                collapseRunning = false;
            }
        });
        collapseThread.setDaemon(true);
        collapseThread.start();
        renderer.iterate();
    }

    /**
     * Stops the continuous iteration mode of the visualization.
     */
    private void stopIteration() {
        System.out.println("stop ...");
        shouldStopCollapse = true;

        while (collapseRunning) {
            try {
                Thread.sleep(10);
            } catch (InterruptedException e) {
                break;
            }
        }

        if (collapseThread != null) {
            collapseThread.interrupt();
        }
        if (renderer != null) {
            renderer.stopIteration();
        }

    }

    /**
     * Stops the animation timer and cleans up resources.
     */
    private void stopAnimation() {
        finished = true;
        if (animationTimer != null) {
            animationTimer.stop();
        }
    }

    /**
     * Cleanup method called when the application is closing.
     * Stops the animation timer and releases resources.
     */
    @Override
    public void stop() {

        stopIteration();
        if (collapseThread != null) {
            collapseThread.interrupt();
        }
        stopAnimation();
        renderer = null;
        System.out.println("Application destroyed");
        Platform.exit();
        System.exit(0);
    }
}