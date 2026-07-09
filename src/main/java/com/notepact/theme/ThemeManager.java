package com.notepact.theme;

import com.notepact.model.Theme;
import javafx.scene.Scene;

import java.util.Objects;
import java.util.logging.Logger;

/**
 * Manages CSS theme switching on the JavaFX scene.
 * Loads base styles + the active theme stylesheet.
 */
public class ThemeManager {

    private static final Logger LOG = Logger.getLogger(ThemeManager.class.getName());
    private static ThemeManager instance;

    private Scene scene;
    private Theme currentTheme = Theme.DARK;

    private static final String BASE_CSS    = "/com/notepact/css/base.css";
    private static final String SYNTAX_CSS  = "/com/notepact/css/syntax.css";

    // ── Singleton ─────────────────────────────────────────────────────────────

    private ThemeManager() {}

    public static synchronized ThemeManager getInstance() {
        if (instance == null) instance = new ThemeManager();
        return instance;
    }

    // ── Initialise with Scene ─────────────────────────────────────────────────

    public void initialize(Scene scene, Theme initial) {
        this.scene = scene;
        applyTheme(initial);
    }

    // ── Theme switching ───────────────────────────────────────────────────────

    public void applyTheme(Theme theme) {
        if (scene == null) return;
        this.currentTheme = theme;

        scene.getStylesheets().clear();

        addCss(BASE_CSS);
        addCss(SYNTAX_CSS);
        addCss(theme.getCssPath());

        LOG.info("Applied theme: " + theme.getDisplayName());
    }

    private void addCss(String path) {
        try {
            var url = getClass().getResource(path);
            if (url != null) {
                scene.getStylesheets().add(url.toExternalForm());
            } else {
                LOG.warning("CSS not found: " + path);
            }
        } catch (Exception e) {
            LOG.warning("Failed to load CSS: " + path + " — " + e.getMessage());
        }
    }

    public void applyTheme(String cssKey) {
        for (Theme t : Theme.values()) {
            if (t.getCssKey().equals(cssKey)) {
                applyTheme(t);
                return;
            }
        }
        applyTheme(Theme.DARK);
    }

    /** Cycles to the next theme in order. */
    public void cycleTheme() {
        Theme[] themes = Theme.values();
        int next = (currentTheme.ordinal() + 1) % themes.length;
        applyTheme(themes[next]);
    }

    public Theme getCurrentTheme() { return currentTheme; }
    public Scene getScene() { return scene; }
}
