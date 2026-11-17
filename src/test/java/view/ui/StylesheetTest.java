package view.ui;

import javafx.css.CssParser;
import javafx.css.Stylesheet;
import org.junit.jupiter.api.Test;

import java.net.URL;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/** Catches syntax mistakes in the stylesheets, which JavaFX otherwise only logs at runtime. */
class StylesheetTest {

    @Test
    void stylesheetsParseWithoutErrors() throws Exception {
        for (String name : List.of("app.css", "contrast.css", "bold-text.css",
                "text-size-up.css", "text-size-down.css")) {
            URL url = getClass().getResource("/css/" + name);
            assertNotNull(url, name);
            CssParser.errorsProperty().clear();
            Stylesheet sheet = new CssParser().parse(url);
            List<String> errors = new ArrayList<>();
            CssParser.errorsProperty().forEach(e -> errors.add(e.toString()));
            assertTrue(errors.isEmpty(), name + ": " + errors);
            assertFalse(sheet.getRules().isEmpty(), name);
        }
    }
}
