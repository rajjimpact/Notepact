package com.notepact.editor;

import com.notepact.model.AppSettings;
import com.notepact.settings.SettingsManager;

import javafx.application.Platform;

import java.util.concurrent.*;
import java.util.function.Supplier;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Schedules periodic auto-saves for the active document.
 * Runs in a background daemon thread, fires a save action on the JavaFX thread.
 */
public class AutoSaveManager {

    private static final Logger LOG = Logger.getLogger(AutoSaveManager.class.getName());
    private static AutoSaveManager instance;

    private final ScheduledExecutorService scheduler = Executors.newSingleThreadScheduledExecutor(r -> {
        Thread t = new Thread(r, "AutoSave");
        t.setDaemon(true);
        return t;
    });

    private ScheduledFuture<?> task;

    private AutoSaveManager() {}

    public static synchronized AutoSaveManager getInstance() {
        if (instance == null) instance = new AutoSaveManager();
        return instance;
    }

    /**
     * Start the auto-save loop. {@code saveAction} is called on the JavaFX thread.
     */
    public void start(Runnable saveAction) {
        stop();
        AppSettings s = SettingsManager.getInstance().getSettings();
        if (!s.isAutoSaveEnabled()) {
            LOG.info("Auto-save disabled.");
            return;
        }

        long intervalSec = s.getAutoSaveInterval();
        task = scheduler.scheduleAtFixedRate(() -> {
            try {
                Platform.runLater(saveAction);
            } catch (Exception e) {
                LOG.log(Level.WARNING, "Auto-save action failed", e);
            }
        }, intervalSec, intervalSec, TimeUnit.SECONDS);

        LOG.info("Auto-save started (interval=" + intervalSec + "s)");
    }

    /** Restart the scheduler (called when settings change). */
    public void restart(Runnable saveAction) {
        start(saveAction);
    }

    public void stop() {
        if (task != null) {
            task.cancel(false);
            task = null;
        }
    }

    public boolean isRunning() {
        return task != null && !task.isDone();
    }

    public void shutdown() {
        stop();
        scheduler.shutdown();
    }
}
