package com.notepact.model;

/**
 * Represents an available editor theme.
 */
public enum Theme {

    DARK("Dark (VS Code)", "dark"),
    LIGHT("Light (Classic)", "light"),
    MIDNIGHT("Midnight (Abyss)", "midnight"),
    SOLARIZED("Solarized Dark", "solarized"),
    MONOKAI("Monokai", "monokai");

    private final String displayName;
    private final String cssKey;

    Theme(String displayName, String cssKey) {
        this.displayName = displayName;
        this.cssKey = cssKey;
    }

    public String getDisplayName() {
        return displayName;
    }

    public String getCssKey() {
        return cssKey;
    }

    /** Returns the CSS resource path for this theme. */
    public String getCssPath() {
        return "/com/notepact/css/" + cssKey + ".css";
    }

    @Override
    public String toString() {
        return displayName;
    }
}
