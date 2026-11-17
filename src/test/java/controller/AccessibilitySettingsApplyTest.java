package controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import javafx.application.Platform;
import javafx.scene.Scene;
import javafx.scene.layout.VBox;
import model.AccessibilitySettings;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import view.ui.UI;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;

class AccessibilitySettingsApplyTest {

    @BeforeAll
    static void initJavaFX() {
        try {
            Platform.startup(() -> {});
        } catch (IllegalStateException ignored) {
            // Toolkit already initialized in another test class.
        }
    }

    private static void onFx(Runnable r) throws Exception {
        CompletableFuture<Void> f = new CompletableFuture<>();
        Platform.runLater(() -> {
            try {
                r.run();
                f.complete(null);
            } catch (Throwable t) {
                f.completeExceptionally(t);
            }
        });
        f.get(10, TimeUnit.SECONDS);
    }

    @Test
    void applySettings_addsAndRemovesSheetsWithoutDuplicates() throws Exception {
        onFx(() -> {
            Scene scene = new Scene(new VBox());
            scene.getStylesheets().add(UI.STYLE);
            AccessibilitySettings s = new AccessibilitySettings(true, true, AccessibilitySettings.TextSize.LARGE);

            MainController.applySettings(scene, s);
            MainController.applySettings(scene, s);
            assertEquals(4, scene.getStylesheets().size());
            assertEquals(UI.STYLE, scene.getStylesheets().get(0), "base sheet stays first so overrides win");
            assertTrue(scene.getStylesheets().contains(UI.TEXT_SIZE_UP_STYLE));

            s.setHighContrast(false);
            s.setTextSize(AccessibilitySettings.TextSize.SMALL);
            MainController.applySettings(scene, s);
            assertFalse(scene.getStylesheets().contains(UI.CONTRAST_STYLE));
            assertFalse(scene.getStylesheets().contains(UI.TEXT_SIZE_UP_STYLE));
            assertTrue(scene.getStylesheets().contains(UI.TEXT_SIZE_DOWN_STYLE));
            assertTrue(scene.getStylesheets().contains(UI.BOLD_STYLE));

            s.setBoldText(false);
            s.setTextSize(AccessibilitySettings.TextSize.MEDIUM);
            MainController.applySettings(scene, s);
            assertEquals(1, scene.getStylesheets().size());
        });
    }

    @Test
    void settingsJson_roundTrips_andToleratesNullOrUnknownFields() throws Exception {
        ObjectMapper json = new ObjectMapper();
        AccessibilitySettings original = new AccessibilitySettings(true, false, AccessibilitySettings.TextSize.SMALL);
        AccessibilitySettings back = json.readValue(json.writeValueAsString(original), AccessibilitySettings.class);
        assertTrue(back.isHighContrast());
        assertFalse(back.isBoldText());
        assertEquals(AccessibilitySettings.TextSize.SMALL, back.getTextSize());

        AccessibilitySettings odd = json.readValue(
                "{\"highContrast\":false,\"boldText\":true,\"textSize\":null,\"futureOption\":1}",
                AccessibilitySettings.class);
        assertTrue(odd.isBoldText());
        assertEquals(AccessibilitySettings.TextSize.MEDIUM, odd.getTextSize());
    }
}
