package principal;

import javafx.scene.image.Image;
import javafx.scene.image.PixelReader;
import javafx.scene.image.PixelWriter;
import javafx.scene.image.WritableImage;

/**
 * Pixel level operations on images.
 *
 * <p>They are plain static procedures with no state and no idea of what a tile
 * is: they take an image and return a new one, leaving the source untouched.
 * That is what makes them usable from the tile set loader, which is the only
 * part of the application that needs derived graphics.</p>
 *
 * <p>Nothing here uses {@link Image} snapshots or effects, because those
 * require the JavaFX toolkit to be running: these loops work on a plain
 * {@link WritableImage} and therefore also work while a set is being loaded in
 * the background.</p>
 */
final class TileImageOps {

    private TileImageOps() {
        // Static procedures only.
    }

    /**
     * Mirrors an image across the vertical axis, so left and right are swapped:
     * the pixel at {@code (x, y)} ends up at {@code (width - 1 - x, y)}.
     *
     * @param source image to mirror
     * @return a new image, same size, mirrored left to right
     */
    static Image mirrorHorizontally(Image source) {
        int width = (int) source.getWidth();
        int height = (int) source.getHeight();
        PixelReader reader = source.getPixelReader();
        WritableImage mirrored = new WritableImage(width, height);
        PixelWriter writer = mirrored.getPixelWriter();
        for (int y = 0; y < height; y++) {
            for (int x = 0; x < width; x++) {
                writer.setArgb(width - 1 - x, y, reader.getArgb(x, y));
            }
        }
        return mirrored;
    }

    /**
     * Mirrors an image across the horizontal axis, so top and bottom are
     * swapped: the pixel at {@code (x, y)} ends up at
     * {@code (x, height - 1 - y)}.
     *
     * @param source image to mirror
     * @return a new image, same size, mirrored top to bottom
     */
    static Image mirrorVertically(Image source) {
        int width = (int) source.getWidth();
        int height = (int) source.getHeight();
        PixelReader reader = source.getPixelReader();
        WritableImage mirrored = new WritableImage(width, height);
        PixelWriter writer = mirrored.getPixelWriter();
        for (int y = 0; y < height; y++) {
            for (int x = 0; x < width; x++) {
                writer.setArgb(x, height - 1 - y, reader.getArgb(x, y));
            }
        }
        return mirrored;
    }

    /**
     * Rotates an image clockwise by a whole number of quarter turns.
     *
     * <p>A quarter turn moves the pixel at {@code (x, y)} to
     * {@code (height - 1 - y, x)}, so width and height are exchanged; a half
     * turn and a three quarter turn are reached by repeating the step, which
     * keeps a single rotation routine to trust.</p>
     *
     * @param source       image to rotate
     * @param quarterTurns number of quarter turns clockwise; any integer works,
     *                     negative values turn counter-clockwise
     * @return a new image; the source itself when the turn is a multiple of four
     */
    static Image rotateClockwise(Image source, int quarterTurns) {
        int turns = Math.floorMod(quarterTurns, 4);
        Image rotated = source;
        for (int i = 0; i < turns; i++) {
            rotated = rotateQuarterTurn(rotated);
        }
        return rotated;
    }

    /**
     * Copies a square region of an image into a new image.
     *
     * @param source source pixels
     * @param startX column of the left edge of the region
     * @param startY row of the top edge of the region
     * @param size   side of the square, in pixels
     * @return a new image holding the region
     * @throws IllegalArgumentException if the size is not positive
     */
    static WritableImage copyRegion(PixelReader source, int startX, int startY, int size) {
        if (size <= 0) {
            throw new IllegalArgumentException("Region size must be positive, got " + size);
        }
        WritableImage region = new WritableImage(size, size);
        PixelWriter writer = region.getPixelWriter();
        for (int y = 0; y < size; y++) {
            for (int x = 0; x < size; x++) {
                writer.setArgb(x, y, source.getArgb(startX + x, startY + y));
            }
        }
        return region;
    }

    private static Image rotateQuarterTurn(Image source) {
        int width = (int) source.getWidth();
        int height = (int) source.getHeight();
        PixelReader reader = source.getPixelReader();
        WritableImage rotated = new WritableImage(height, width);
        PixelWriter writer = rotated.getPixelWriter();
        for (int y = 0; y < height; y++) {
            for (int x = 0; x < width; x++) {
                writer.setArgb(height - 1 - y, x, reader.getArgb(x, y));
            }
        }
        return rotated;
    }
}
