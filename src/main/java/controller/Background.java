package controller;

import javafx.application.Platform;

import java.util.function.Consumer;
import java.util.function.Supplier;

/**
 * Runs slow work (database calls, password hashing, sending email) off the JavaFX
 * application thread and hands the result back on it, so the window stays responsive.
 */
final class Background {

    private Background() {}

    static <T> void run(Supplier<T> work, Consumer<T> onSuccess, Consumer<Throwable> onFailure) {
        Thread t = new Thread(() -> {
            try {
                T result = work.get();
                Platform.runLater(() -> onSuccess.accept(result));
            } catch (Throwable ex) {
                Platform.runLater(() -> onFailure.accept(ex));
            }
        }, "auth-background");
        t.setDaemon(true);
        t.start();
    }
}
