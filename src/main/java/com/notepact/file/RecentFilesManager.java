package com.notepact.file;

import com.notepact.db.DatabaseManager;
import javafx.application.Platform;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;

import java.io.File;
import java.util.List;
import java.util.logging.Logger;

/**
 * Manages the recent files list backed by SQLite.
 * Provides an observable list for UI binding.
 */
public class RecentFilesManager {

    private static final Logger LOG = Logger.getLogger(RecentFilesManager.class.getName());
    private static RecentFilesManager instance;

    private final ObservableList<String> recentFiles = FXCollections.observableArrayList();

    private RecentFilesManager() {
        reload();
    }

    public static synchronized RecentFilesManager getInstance() {
        if (instance == null) instance = new RecentFilesManager();
        return instance;
    }

    public void reload() {
        List<String> paths = DatabaseManager.getInstance().getRecentFiles();
        Platform.runLater(() -> {
            recentFiles.setAll(paths);
        });
    }

    public void add(String path) {
        DatabaseManager.getInstance().addRecentFile(path);
        reload();
    }

    public void clear() {
        DatabaseManager.getInstance().clearRecentFiles();
        Platform.runLater(recentFiles::clear);
    }

    public ObservableList<String> getRecentFiles() {
        return recentFiles;
    }

    /** Returns just the filename from a path, for display. */
    public static String displayName(String path) {
        return new File(path).getName();
    }
}
