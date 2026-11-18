package principal;

import javafx.animation.AnimationTimer;
import javafx.application.Application;
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

import java.util.Random;

/**
 * Wave Function Collapse visualization.
 *
 * <p>A JavaFX application that grows a board of tiles in real time, starting
 * from a single tile and adding compatible neighbours until the board is full
 * or no solution is found. The window offers three controls: restart the
 * generation, stop it, and change the tile set or the zoom level.</p>
 *
 * <p>Accepted command line arguments:</p>
 *
 * <pre>
 * --width=800    width of the canvas in pixels
 * --height=800   height of the canvas in pixels
 * --set=circuit  tile set to start with, by folder name (see {@link TileSet})
 * --seed=42      seed of the generation; the same seed draws the same pattern
 * </pre>
 *
 * <p>Without {@code --seed} a new random seed is used on every restart and
 * printed to the console, so any pattern can be reproduced later.</p>
 */
public class WaveFunction extends Application {

    /** Canvas width used when {@code --width} is not given. */
    private static final int DEFAULT_WIDTH = 800;
    /** Canvas height used when {@code --height} is not given. */
    private static final int DEFAULT_HEIGHT = 800;
    /** Zoom used when the user has not chosen another one. */
    private static final int DEFAULT_PIXEL_SIZE = 4;
    /** Height of the controls bar, in pixels. */
    private static final int BUTTON_HEIGHT = 25;
    /** Colour of the empty area of the canvas. */
    private static final Color BACKGROUND_COLOR = Color.LIGHTGRAY;
    /** Zoom levels offered to the user. */
    private static final Integer[] PIXEL_SIZES = {1, 2, 3, 4, 5, 6, 7, 8};

    private int canvasWidth = DEFAULT_WIDTH;
    private int canvasHeight = DEFAULT_HEIGHT;
    private int pixelSize = DEFAULT_PIXEL_SIZE;
    private Long pinnedSeed;
    private TileSet activeSet = TileSet.CIRCUIT;

    private Canvas canvas;
    private Renderer renderer;
    private WfcSolver solver;
    private AnimationTimer animationTimer;
    private boolean finished;

    /**
     * Launches the application.
     *
     * @param args command line arguments, see the class documentation
     */
    public static void main(String[] args) {
        launch(args);
    }

    @Override
    public void start(Stage primaryStage) {
        parseArguments(getParameters());

        primaryStage.setTitle("Wave Function Collapse");
        initialize();

        BorderPane root = new BorderPane();
        root.setCenter(canvas);
        root.setBottom(buildControls());

        Scene scene = new Scene(root, canvasWidth, canvasHeight + BUTTON_HEIGHT);
        primaryStage.setScene(scene);
        primaryStage.setResizable(false);
        primaryStage.show();

        // Without this the window would show an empty canvas until the user
        // pressed "Restart".
        restart();
    }

    @Override
    public void stop() {
        finished = true;
        if (animationTimer != null) {
            animationTimer.stop();
        }
        if (solver != null) {
            solver.shutdown();
        }
        System.out.println("Application closed");
    }

    private void initialize() {
        canvas = new Canvas(canvasWidth, canvasHeight);
        GraphicsContext gc = canvas.getGraphicsContext2D();

        renderer = new Renderer(gc, BACKGROUND_COLOR);
        renderer.setPixelSize(pixelSize);
        renderer.clearCanvas();

        solver = new WfcSolver();

        animationTimer = new AnimationTimer() {
            @Override
            public void handle(long now) {
                if (!finished) {
                    renderer.updateFrame();
                }
            }
        };
        animationTimer.start();
    }

    private HBox buildControls() {
        Button restartButton = new Button("Restart");
        restartButton.setPrefHeight(BUTTON_HEIGHT);
        restartButton.setOnAction(e -> restart());

        Button stopButton = new Button("Stop");
        stopButton.setPrefHeight(BUTTON_HEIGHT);
        stopButton.setOnAction(e -> stopGeneration());

        ComboBox<TileSet> setSelector = new ComboBox<>();
        setSelector.getItems().addAll(TileSet.values());
        setSelector.setValue(activeSet);
        setSelector.setPrefHeight(BUTTON_HEIGHT);
        setSelector.setOnAction(e -> {
            TileSet selected = setSelector.getValue();
            if (selected == null || selected == activeSet) {
                return;
            }
            activeSet = selected;
            restart();
        });

        ComboBox<Integer> pixelSizeSelector = new ComboBox<>();
        pixelSizeSelector.getItems().addAll(PIXEL_SIZES);
        pixelSizeSelector.setValue(pixelSize);
        pixelSizeSelector.setPrefHeight(BUTTON_HEIGHT);
        pixelSizeSelector.setOnAction(e -> {
            Integer selected = pixelSizeSelector.getValue();
            if (selected == null || selected == pixelSize) {
                return;
            }
            pixelSize = selected;
            restart();
        });

        HBox controls = new HBox();
        controls.setPadding(new Insets(0, 10, 0, 0));
        controls.setSpacing(10);
        controls.getChildren().addAll(restartButton, stopButton, setSelector, pixelSizeSelector);
        return controls;
    }

    /**
     * Throws the current board away and starts a new generation.
     *
     * <p>Nothing in here blocks: the previous generation is asked to stop and
     * the new one is queued behind it, so the user interface stays responsive
     * even while a large board is being generated. Each generation gets a fresh
     * board and a fresh drawing session, so a run that is still dying out can
     * neither paint its tiles over the new board nor mix its progress with the
     * new run. The tile set itself is shared: it is loaded once and never
     * changes afterwards.</p>
     */
    private void restart() {
        if (renderer == null) {
            return;
        }

        solver.stop();

        long seed = pinnedSeed != null ? pinnedSeed : new Random().nextLong();
        System.out.println("Restarting " + activeSet + " with seed " + seed);

        int cell = activeSet.getCellSize() * pixelSize;
        int overlap = activeSet.usesOverlap() ? activeSet.getTileSize() * pixelSize : 0;
        int columns = boardDimension(canvasWidth, cell, overlap);
        int rows = boardDimension(canvasHeight, cell, overlap);
        TileBoard board = new TileBoard(activeSet, columns, rows);

        renderer.setPixelSize(pixelSize);
        renderer.clearCanvas();

        solver.setSeed(seed);
        solver.solve(board, renderer.newSession(board));
    }

    /** Calculates the cells needed to cover the canvas using the set's anchors. */
    private static int boardDimension(int canvasSize, int cell, int overlap) {
        if (overlap == 0) {
            return Math.max(1, canvasSize / cell);
        }
        return Math.max(1, (int) Math.ceil((canvasSize - overlap) / (double) cell));
    }

    /**
     * Stops the generation and discards pending drawing actions, leaving the
     * canvas as it is.
     */
    private void stopGeneration() {
        solver.stop();
        renderer.stopSession();
        System.out.println("Generation stopped");
    }

    private void parseArguments(Parameters params) {
        canvasWidth = parsePositiveInt(params, "width", DEFAULT_WIDTH);
        canvasHeight = parsePositiveInt(params, "height", DEFAULT_HEIGHT);

        String rawSet = params.getNamed().get("set");
        if (rawSet != null) {
            TileSet selected = parseTileSet(rawSet);
            if (selected != null) {
                activeSet = selected;
            } else {
                System.err.println("Unknown tile set '" + rawSet + "', using " + activeSet);
            }
        }

        String rawSeed = params.getNamed().get("seed");
        if (rawSeed == null) {
            return;
        }
        try {
            pinnedSeed = Long.parseLong(rawSeed);
        } catch (NumberFormatException e) {
            System.err.println("Invalid seed '" + rawSeed + "', a random seed will be used");
        }
    }

    private static TileSet parseTileSet(String name) {
        for (TileSet set : TileSet.values()) {
            if (set.getFolderName().equalsIgnoreCase(name) || set.name().equalsIgnoreCase(name)) {
                return set;
            }
        }
        return null;
    }

    private static int parsePositiveInt(Parameters params, String name, int defaultValue) {
        String raw = params.getNamed().get(name);
        if (raw == null) {
            return defaultValue;
        }
        try {
            int value = Integer.parseInt(raw);
            if (value > 0) {
                return value;
            }
            System.err.println("--" + name + " must be positive, using " + defaultValue);
        } catch (NumberFormatException e) {
            System.err.println("Invalid --" + name + " value '" + raw + "', using " + defaultValue);
        }
        return defaultValue;
    }
}
