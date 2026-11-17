package model;

import javafx.beans.property.BooleanProperty;
import javafx.beans.property.ObjectProperty;
import javafx.beans.property.SimpleBooleanProperty;
import javafx.beans.property.SimpleObjectProperty;

@com.fasterxml.jackson.annotation.JsonIgnoreProperties(ignoreUnknown = true)
public class AccessibilitySettings {

    private final BooleanProperty highContrast = new SimpleBooleanProperty(false);
    private final BooleanProperty boldText = new SimpleBooleanProperty(false);
    private final ObjectProperty<TextSize> textSize = new SimpleObjectProperty<>(TextSize.MEDIUM);

    public enum TextSize {
        SMALL, MEDIUM, LARGE
    }

    public AccessibilitySettings() {}

    public AccessibilitySettings(boolean highContrast, boolean boldText, TextSize textSize) {
        this.highContrast.set(highContrast);
        this.boldText.set(boldText);
        setTextSize(textSize);
    }

    public boolean isHighContrast() {
        return highContrast.get();
    }
    public void setHighContrast(boolean highContrast) {
        this.highContrast.set(highContrast);
    }
    public boolean isBoldText() {
        return boldText.get();
    }
    public void setBoldText(boolean boldText) {
        this.boldText.set(boldText);
    }
    public TextSize getTextSize() {
        return textSize.get();
    }
    public void setTextSize(TextSize textSize) {
        // stored JSON may contain null or come from an older version; never leave the size unset
        this.textSize.set(textSize != null ? textSize : TextSize.MEDIUM);
    }

    @com.fasterxml.jackson.annotation.JsonIgnore
    public BooleanProperty highContrastProperty() {
        return highContrast;
    }
    @com.fasterxml.jackson.annotation.JsonIgnore
    public BooleanProperty boldTextProperty() {
        return boldText;
    }
    @com.fasterxml.jackson.annotation.JsonIgnore
    public ObjectProperty<TextSize> textSizeProperty() {
        return textSize;
    }
}
