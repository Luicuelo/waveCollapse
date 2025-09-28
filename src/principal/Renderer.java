package principal;

import javafx.scene.canvas.GraphicsContext;
import javafx.scene.image.Image;
import javafx.scene.paint.Color;
import principal.TitlesManager.TitleSet;
import principal.TitlesManager.TitleSet.SetType;

import java.util.LinkedList;
import java.util.Queue;

/**
 * Renderer class for visualizing tiles in the Wave Function Collapse algorithm.
 *<p>
 * This class handles the graphical representation of tiles on a grid-based
 * canvas.
 * It supports animation through an action queue system that can draw tiles with
 * step-by-step or continuous visualization modes.
 * </p>
 */
class Renderer {

    /**
     * Queue of actions to be performed during animation
*/
    protected static final Queue<Action> actionQueue = new LinkedList<>();
    /**
     * JavaFX graphics context for drawing
     */
    private final GraphicsContext gc;

    /**
     * Background color for the canvas
     */
    protected Color backGroundColor;
    /**
     * Size of each cell in pixels
*/
    protected int PIXEL_SIZE;
    /**
     * Flag indicating if continuous iteration is active
     */
    private boolean runningIteration = false;
    /**
     * Flag indicating if single step mode is active
     */
    private boolean runningStep = false;

    /**
     * Creates a new Renderer for tile visualization.
*
     * @param gc              the graphics context to draw on (must not be null)
     * @param w               canvas width in pixels (must be positive)
     * @param h               canvas height in pixels (must be positive)
     * @param pPIXEL_SIZE     the size of each pixel inthe visualization
     * @param backGroundColor color for the background
     * @throws IllegalArgumentException if gc is null or dimensions are invalid
     */
    public Renderer(GraphicsContext gc, int w, int h, int pPIXEL_SIZE, Color backGroundColor) {
        PIXEL_SIZE = pPIXEL_SIZE;
if (gc == null) {
            throw new IllegalArgumentException("GraphicsContext cannot be null");
        }
        if (w <= 0 || h <= 0) {
            throw new IllegalArgumentException("Width and height must be positive");
        }

        this.backGroundColor = backGroundColor;
        this.gc = gc;

}

    /**
     * Updates the frame by processing one iteration if running.
     * This method is called by the animation timer.
     */
    public void updateFrame() {
        if (!runningIteration && !runningStep)
            return;
        iteration();
        runningStep = false;
    }

    /**
     * Processesone iteration of the animation queue.
     * Executes the current action and removes it when complete.
     */
    public void iteration() {
        if (actionQueue.isEmpty()) {
            return;
        }
        Action action = actionQueue.peek();
        // Add null check to prevent NullPointerException
        if (action == null) {
return;
        }
        boolean finished;
        switch (action.getInstruction()) {
            case DRAW_TitleS:
                finished = drawTitles(action);
                break;
            case ADD_Title:
                finished = drawTitle(action);
                break;
            case DEL_Title:
                finished = delTitle(action);
                break;

            default:
                // Remove unknown actions from the queue
                actionQueue.poll();
                return;
        }
        if (finished) {
            actionQueue.poll();
        }
    }

    /**
     * Draws tiles according to the specified action.
     *
     * @param action the action containing tile drawing information
     * @returntrue if the drawing is complete, false otherwise
     */
    private boolean drawTitles(Action action) {
        int tileNumber = action.getTotalStages() - action.getStage() + 1;
        // System.out.println("Drawing tile " + tileNumber + " of " +
        // action.getTotalStages());
       TitlesManager.PointInt pos = TitlesManager.TitleBoard.getTitlePosByNumber(tileNumber);
        if (pos != null) {
            int tileIndex = TitlesManager.TitleBoard.getTitleIndex(pos.x(), pos.y());
            // System.out.println("Position: (" + pos.getX() + "," + pos.getY() + "),
            //tileIndex: " + tileIndex);            
            drawTile(tileIndex, pos.x(), pos.y(),TitlesManager.getTitleSize(tileIndex),false);
        } else {
            // System.out.println("Position is null for tile number " + tileNumber);
        }
        return action.endStage();
    }

    /**
     *Draws a single tile according to the specified action.
     *
     * @param action the action containing tile drawing information
     * @return true if the drawing is complete, false otherwise
     */
    private boolean drawTitle(Action action) {
        int tileIndex = TitlesManager.TitleBoard.getTitleIndex(action.x, action.y);
        if (tileIndex==-1) return action.endStage(); 
        boolean overlap= false;
        TitlesManager.Title title=TitlesManager.getTitle(tileIndex);
        if(title!=null) overlap=(title.getSet().getSetType().hasOverload());
        drawTile(tileIndex, action.x, action.y,TitlesManager.getTitleSize(tileIndex),overlap);
        return action.endStage();
    }

    /**
     * Removes a tile according to the specified action.
     *
     * @param action the action containing tile removal information
     * @return true if the removal is complete, false otherwise
     */
    private boolean delTitle(Action action) {
        int tileIndex = TitlesManager.TitleBoard.getTitleIndex(action.x, action.y); 
        if (tileIndex==-1) return action.endStage(); 
        boolean overlap= false;
        TitlesManager.Title title=TitlesManager.getTitle(tileIndex);
        if(title!=null) overlap=(title.getSet().getSetType().hasOverload());
        delTile(action.x, action.y,TitlesManager.getTitleSize(tileIndex),overlap);
        return action.endStage();
    }

    /**
     * Restarts the visualization by stopping any ongoing animation and clearing the
     * action queue.
     * Resets the animation state to allow for a fresh start.
     */
    public void restart() {
        actionQueue.clear();
        runningIteration = false;
    }

    /**
     * Activates continuous iteration mode.
     * When active, the visualization will continuously process animation actions.
     */
    public void iterate() {
        runningIteration = true;
    }

    /**
     * Stops the continuous iteration mode.
    * When stopped, the visualization will pause and wait for manual control.
     */
    public void stopIteration() {
        runningIteration = false;
    }

    /**
     * Draws a tile at the specified position
     *
     * @param i the index of the tile to draw
     * @param x the xposition to draw the tile
     * @param y the y position to draw the tile
     * @param size the size of the tile
     */
    public void drawTile(int i, int x, int y,int size,boolean overlap) {
        // Get the tile from CircuitTitles
        TitlesManager.Title tile = TitlesManager.getTitle(i);
        if (tile == null) {
            return;
        }

        // Get the tile image
        Image tileImage = tile.image;
        if (tileImage == null) {
            return;
        }

        // Calculate screen position
        int screenX = x * PIXEL_SIZE *(overlap?3:size);
        int screenY = y * PIXEL_SIZE * (overlap?3:size);

        // Draw the tile image scaled so each pixel becomes a PIXEL_SIZE square
        // If the image is 14x14 and PIXEL_SIZE is 4, the drawn size should be 56x56
        // (14*4 x 14*4)
        
        // pixel-perfect
        gc.setImageSmoothing(false);
        double drawnWidth = tileImage.getWidth() * PIXEL_SIZE* (overlap?3:1);
        double drawnHeight = tileImage.getHeight() * PIXEL_SIZE*(overlap?3:1);
        gc.drawImage(tileImage, screenX, screenY, drawnWidth, drawnHeight);
    }

    /**
     * Removes a tile from the specified position by clearing the area with the
     * background color.
     *
     * @param x the x position of the tile to remove* @param y the y position of the tile to remove
     * @param size the size of the tile
     */
    public void delTile(int x, int y,int size,boolean overlap) {
        // Calculate screen position and size
        int screenX = x * PIXEL_SIZE * (overlap?3:size);
        int screenY = y * PIXEL_SIZE * (overlap?3:size);
        int tileSize = (overlap?3:size) * PIXEL_SIZE;

        // Clear the area by filling it with the background color
        gc.setFill(backGroundColor);
        gc.fillRect(screenX, screenY, tileSize,tileSize);
    }

    /**
     * Represents an action in the animation queue.
     * <p>
     * Actions define operations to be performed during the visualization,
     * such as drawing tiles. Each action can have multiple stages for animation
     * purposes.
     * </p>
     */
    protected static class Action{

        /**
         * X coordinate for the action
         */
        private final int x;
        /**
         * Y coordinate for the action
         */
        private final int y;
        /**
         * The type of operation
         */
        private final Operations instruction;
        /**
         * Total number of stages for this action*/
        private final int totalStages;
        /**
         * Current animation stage counter
         */
        private int stage;

        /**
         * Creates a new action for the animation queue.
         *
         * @param instruction the type of operation (DRAW_TitleS)
         * @param x           first parameter (unused for DRAW_TitleS)
         * @param y           second parameter (unused for DRAW_TitleS)
         */
        public Action(Operations instruction, int x, int y) {
            this.instruction = instruction;
            this.x = x;
            this.y = y;
            this.totalStages = getInstructionStageNumber();
            this.stage = totalStages;
        }

        /**
         * Gets the number of animation stages for this instruction type.
         *
         * @return number of stages based on the number of tiles on the board
         */
        private int getInstructionStageNumber() {
            return switch (instruction) {
               case DRAW_TitleS -> TitlesManager.TitleBoard.getNumberOfTitles();
                case ADD_Title, DEL_Title -> 1;
            };
        }

        /**
         * Gets the instruction type.
         *
         * @return the instruction type
         */
        public Operations getInstruction() {
            return instruction;
        }

        /**
        * Advances the animation by one stage.
         *
         * @return true if the animation is complete, false if more stages remain
         */
        public boolean endStage() {
            stage--;
            return stage <= 0;
        }

        /**
         * Gets the current stage number.
         *
         * @return thecurrent stage number
         */
        public int getStage() {
            return stage;
        }

        /**
         * Gets the total number of stages.
         *
         * @return the total number of stages
         */
        public int getTotalStages() {
            return totalStages;
        }

        /**
         * Enumrepresenting the different types of operations that can be performed.
         */
        public enum Operations {
            /**
             * Operation to draw tiles on the board
             */
            DRAW_TitleS,
            /**
             * Operation to add a tile to the board
             */
            ADD_Title,
            /**
             * Operation to remove a tilefrom the board
             */
            DEL_Title
        }

    }

}