package com.notepact.file;

import com.notepact.db.DatabaseManager;
import com.notepact.db.DatabaseManager.SessionEntry;

import java.util.List;
import java.util.function.Consumer;
import java.util.function.Supplier;
import java.util.logging.Logger;

/**
 * Saves and restores editor sessions (open tabs + content + caret positions).
 * Called on application shutdown and startup.
 */
public class SessionManager {

    private static final Logger LOG = Logger.getLogger(SessionManager.class.getName());
    private static SessionManager instance;

    private SessionManager() {}

    public static synchronized SessionManager getInstance() {
        if (instance == null) instance = new SessionManager();
        return instance;
    }

    /**
     * Persist the current session to SQLite.
     * @param entries list of tab session data to save
     */
    public void saveSession(List<SessionEntry> entries) {
        try {
            DatabaseManager.getInstance().saveSession(entries);
            LOG.info("Session saved: " + entries.size() + " tab(s)");
        } catch (Exception e) {
            LOG.warning("Failed to save session: " + e.getMessage());
        }
    }

    /**
     * Load the previously saved session from SQLite.
     * @return list of session entries, possibly empty if no session exists
     */
    public List<SessionEntry> loadSession() {
        try {
            List<SessionEntry> entries = DatabaseManager.getInstance().loadSession();
            LOG.info("Session loaded: " + entries.size() + " tab(s)");
            return entries;
        } catch (Exception e) {
            LOG.warning("Failed to load session: " + e.getMessage());
            return List.of();
        }
    }

    /**
     * Clears the stored session data.
     */
    public void clearSession() {
        DatabaseManager.getInstance().saveSession(List.of());
    }
}
