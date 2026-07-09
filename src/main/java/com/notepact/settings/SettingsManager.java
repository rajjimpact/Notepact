package com.notepact.settings;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.notepact.model.AppSettings;

import java.io.File;
import java.io.IOException;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Loads and saves application settings to JSON at ~/.notepact/settings.json
 */
public class SettingsManager {

    private static final Logger LOG = Logger.getLogger(SettingsManager.class.getName());
    private static SettingsManager instance;

    private final File settingsFile;
    private final ObjectMapper mapper;
    private AppSettings settings;

    // ── Singleton ─────────────────────────────────────────────────────────────

    private SettingsManager() {
        String home = System.getProperty("user.home");
        File appDir = new File(home, ".notepact");
        appDir.mkdirs();
        this.settingsFile = new File(appDir, "settings.json");
        this.mapper = new ObjectMapper().enable(SerializationFeature.INDENT_OUTPUT);
        this.settings = load();
    }

    public static synchronized SettingsManager getInstance() {
        if (instance == null) instance = new SettingsManager();
        return instance;
    }

    // ── Load / Save ───────────────────────────────────────────────────────────

    private AppSettings load() {
        if (settingsFile.exists()) {
            try {
                return mapper.readValue(settingsFile, AppSettings.class);
            } catch (IOException e) {
                LOG.log(Level.WARNING, "Failed to load settings, using defaults", e);
            }
        }
        return new AppSettings();
    }

    public void save() {
        try {
            mapper.writeValue(settingsFile, settings);
        } catch (IOException e) {
            LOG.log(Level.WARNING, "Failed to save settings", e);
        }
    }

    public AppSettings getSettings() {
        return settings;
    }

    public void resetToDefaults() {
        settings = new AppSettings();
        save();
    }
}
