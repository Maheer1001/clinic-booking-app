package dev;

import javafx.application.Platform;
import javafx.geometry.Bounds;
import javafx.scene.Node;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.SnapshotParameters;
import javafx.scene.control.Labeled;
import javafx.scene.control.TextInputControl;
import javafx.scene.image.PixelReader;
import javafx.scene.image.WritableImage;
import javafx.scene.image.WritablePixelFormat;
import javafx.scene.paint.Color;
import javafx.scene.transform.Transform;
import javafx.stage.PopupWindow;
import javafx.stage.Stage;
import javafx.stage.Window;

import javax.imageio.ImageIO;
import java.awt.AlphaComposite;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.function.Predicate;
import java.util.function.Supplier;

/**
 * Small helpers for driving the real UI from a script thread and saving screenshots.
 * Used only by {@link ScreenshotHarness} (developer tool, not part of the app or the test suite).
 */
final class Fx {

    /** Screenshots are rendered at this multiple of the window size, so they stay sharp when scaled. */
    static final double SCALE = 2;

    private Fx() {}

    /** Runs on the FX thread and waits for it to finish. Do not use for actions that open a modal dialog. */
    static <T> T call(Supplier<T> work) {
        if (Platform.isFxApplicationThread()) return work.get();
        CompletableFuture<T> f = new CompletableFuture<>();
        Platform.runLater(() -> {
            try {
                f.complete(work.get());
            } catch (Throwable t) {
                f.completeExceptionally(t);
            }
        });
        try {
            return f.get(60, TimeUnit.SECONDS);
        } catch (Exception e) {
            throw new IllegalStateException(e.getCause() != null ? e.getCause() : e);
        }
    }

    static void run(Runnable work) {
        call(() -> {
            work.run();
            return null;
        });
    }

    /** Queues an action without waiting (for buttons whose handler calls showAndWait). */
    static void later(Runnable work) {
        Platform.runLater(work);
    }

    static void sleep(long ms) {
        try {
            Thread.sleep(ms);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    /** Lets layout, CSS and a couple of pulses happen. */
    static void settle() {
        for (int i = 0; i < 3; i++) {
            run(() -> {});
            sleep(120);
        }
    }

    static boolean waitFor(Supplier<Boolean> condition, long timeoutMs) {
        long end = System.currentTimeMillis() + timeoutMs;
        while (System.currentTimeMillis() < end) {
            if (Boolean.TRUE.equals(call(condition))) return true;
            sleep(100);
        }
        return false;
    }

    // ---- Finding nodes -------------------------------------------------------------------------

    static List<Node> all(Parent root) {
        List<Node> out = new ArrayList<>();
        collect(root, out);
        return out;
    }

    private static void collect(Node n, List<Node> out) {
        out.add(n);
        if (n instanceof Parent p) {
            for (Node c : p.getChildrenUnmodifiable()) collect(c, out);
        }
    }

    static <T extends Node> Optional<T> find(Window w, Class<T> type, Predicate<T> test) {
        return call(() -> {
            if (w == null || w.getScene() == null) return Optional.<T>empty();
            for (Node n : all(w.getScene().getRoot())) {
                if (type.isInstance(n) && n.isVisible() && test.test(type.cast(n))) {
                    return Optional.of(type.cast(n));
                }
            }
            return Optional.<T>empty();
        });
    }

    static <T extends Node> T require(Window w, Class<T> type, Predicate<T> test, String what) {
        long end = System.currentTimeMillis() + 8000;
        while (System.currentTimeMillis() < end) {
            Optional<T> found = find(w, type, test);
            if (found.isPresent()) return found.get();
            sleep(150);
        }
        throw new IllegalStateException("Not found: " + what);
    }

    static <T extends Labeled> T labeled(Window w, Class<T> type, String text) {
        return require(w, type, l -> text.equals(l.getText()), type.getSimpleName() + " '" + text + "'");
    }

    static void type(TextInputControl field, String text) {
        run(() -> {
            field.requestFocus();
            field.setText(text);
            field.positionCaret(text.length());
        });
    }

    /** The showing modal dialog / extra stage other than {@code main}, if any. */
    static Optional<Stage> otherStage(Stage main) {
        return call(() -> {
            for (Window w : Window.getWindows()) {
                if (w instanceof Stage s && s != main && s.isShowing()) return Optional.of(s);
            }
            return Optional.<Stage>empty();
        });
    }

    static Stage awaitOtherStage(Stage main) {
        long end = System.currentTimeMillis() + 10000;
        while (System.currentTimeMillis() < end) {
            Optional<Stage> s = otherStage(main);
            if (s.isPresent()) {
                settle();
                return s.get();
            }
            sleep(100);
        }
        throw new IllegalStateException("No dialog appeared");
    }

    // ---- Snapshots -----------------------------------------------------------------------------

    static BufferedImage snapshot(Scene scene) {
        return call(() -> {
            SnapshotParameters p = new SnapshotParameters();
            p.setTransform(Transform.scale(SCALE, SCALE));
            p.setFill(Color.WHITE);
            WritableImage img = scene.getRoot().snapshot(p, null);
            return toBuffered(img);
        });
    }

    /** The window's scene plus any open popups (combo lists, date pickers, tooltips) drawn in place. */
    static BufferedImage snapshotWithPopups(Stage stage) {
        BufferedImage base = snapshot(stage.getScene());
        List<Object[]> popups = call(() -> {
            List<Object[]> list = new ArrayList<>();
            for (Window w : Window.getWindows()) {
                if (w instanceof PopupWindow pw && pw.isShowing() && pw.getScene() != null) {
                    double x = pw.getX() - stage.getX() - stage.getScene().getX();
                    double y = pw.getY() - stage.getY() - stage.getScene().getY();
                    SnapshotParameters p = new SnapshotParameters();
                    p.setTransform(Transform.scale(SCALE, SCALE));
                    p.setFill(Color.TRANSPARENT);
                    Node root = pw.getScene().getRoot();
                    Bounds b = root.getBoundsInParent();
                    list.add(new Object[]{toBuffered(root.snapshot(p, null)), x + b.getMinX(), y + b.getMinY()});
                }
            }
            return list;
        });
        if (popups.isEmpty()) return base;
        Graphics2D g = base.createGraphics();
        for (Object[] pop : popups) {
            g.drawImage((BufferedImage) pop[0], (int) Math.round((double) pop[1] * SCALE),
                    (int) Math.round((double) pop[2] * SCALE), null);
        }
        g.dispose();
        return base;
    }

    /** The main window dimmed, with the dialog drawn centred over it, as the user sees it. */
    static BufferedImage withDialog(BufferedImage main, BufferedImage dialog) {
        BufferedImage out = new BufferedImage(main.getWidth(), main.getHeight(), BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = out.createGraphics();
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g.drawImage(main, 0, 0, null);
        g.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, 0.45f));
        g.setColor(new java.awt.Color(20, 10, 35));
        g.fillRect(0, 0, main.getWidth(), main.getHeight());
        g.setComposite(AlphaComposite.SrcOver);
        double scale = Math.min(1.0, (main.getWidth() - 40.0) / dialog.getWidth());
        int w = (int) (dialog.getWidth() * scale);
        int h = (int) (dialog.getHeight() * scale);
        int x = (main.getWidth() - w) / 2;
        int y = (main.getHeight() - h) / 2;
        g.setColor(new java.awt.Color(0, 0, 0, 40));
        for (int i = 1; i <= 8; i++) g.fillRoundRect(x - i, y - i + 4, w + 2 * i, h + 2 * i, 24 + i, 24 + i);
        g.drawImage(dialog, x, y, w, h, null);
        g.dispose();
        return out;
    }

    static BufferedImage toBuffered(WritableImage img) {
        int w = (int) img.getWidth();
        int h = (int) img.getHeight();
        int[] px = new int[w * h];
        PixelReader r = img.getPixelReader();
        r.getPixels(0, 0, w, h, WritablePixelFormat.getIntArgbInstance(), px, 0, w);
        BufferedImage out = new BufferedImage(w, h, BufferedImage.TYPE_INT_ARGB);
        out.setRGB(0, 0, w, h, px, 0, w);
        return out;
    }

    static void save(BufferedImage img, Path file) {
        try {
            Files.createDirectories(file.getParent());
            ImageIO.write(img, "png", file.toFile());
            System.out.println("[shot] " + file.getFileName());
        } catch (IOException e) {
            throw new IllegalStateException(e);
        }
    }
}
