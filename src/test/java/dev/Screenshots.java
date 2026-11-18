package dev;

/**
 * Entry point for {@link ScreenshotHarness}. A separate class because the JavaFX launcher refuses to
 * start an Application subclass directly from the class path.
 */
public final class Screenshots {
    private Screenshots() {}

    public static void main(String[] args) {
        ScreenshotHarness.main(args);
    }
}
