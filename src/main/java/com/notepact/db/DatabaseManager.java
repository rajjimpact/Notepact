package com.notepact.db;

import com.notepact.model.BookmarkModel;
import com.notepact.model.SnippetModel;

import java.io.File;
import java.sql.*;
import java.util.ArrayList;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Manages the SQLite database for persistent storage:
 * – recent_files
 * – bookmarks
 * – snippets
 * – clipboard_history
 * – sessions
 */
public class DatabaseManager {

    private static final Logger LOG = Logger.getLogger(DatabaseManager.class.getName());
    private static DatabaseManager instance;

    private Connection connection;
    private final String dbPath;

    // ── Singleton ─────────────────────────────────────────────────────────────

    private DatabaseManager() {
        String home = System.getProperty("user.home");
        File appDir = new File(home, ".notepact");
        appDir.mkdirs();
        this.dbPath = new File(appDir, "notepact.db").getAbsolutePath();
        connect();
        initSchema();
    }

    public static synchronized DatabaseManager getInstance() {
        if (instance == null) instance = new DatabaseManager();
        return instance;
    }

    // ── Connection ────────────────────────────────────────────────────────────

    private void connect() {
        try {
            Class.forName("org.sqlite.JDBC");
            connection = DriverManager.getConnection("jdbc:sqlite:" + dbPath);
            connection.setAutoCommit(true);
            // Enable WAL mode for performance
            try (Statement st = connection.createStatement()) {
                st.execute("PRAGMA journal_mode=WAL");
                st.execute("PRAGMA synchronous=NORMAL");
                st.execute("PRAGMA cache_size=10000");
            }
            LOG.info("Database connected: " + dbPath);
        } catch (Exception e) {
            LOG.log(Level.SEVERE, "Failed to connect to database", e);
        }
    }

    // ── Schema ────────────────────────────────────────────────────────────────

    private void initSchema() {
        try (Statement st = connection.createStatement()) {

            st.execute("""
                CREATE TABLE IF NOT EXISTS recent_files (
                    id        INTEGER PRIMARY KEY AUTOINCREMENT,
                    path      TEXT UNIQUE NOT NULL,
                    opened_at INTEGER DEFAULT (strftime('%s','now'))
                )
            """);

            st.execute("""
                CREATE TABLE IF NOT EXISTS bookmarks (
                    id          INTEGER PRIMARY KEY AUTOINCREMENT,
                    file_path   TEXT NOT NULL,
                    line_number INTEGER NOT NULL,
                    label       TEXT,
                    created_at  INTEGER DEFAULT (strftime('%s','now')),
                    UNIQUE(file_path, line_number)
                )
            """);

            st.execute("""
                CREATE TABLE IF NOT EXISTS snippets (
                    id         INTEGER PRIMARY KEY AUTOINCREMENT,
                    name       TEXT NOT NULL,
                    category   TEXT DEFAULT 'General',
                    content    TEXT NOT NULL,
                    shortcut   TEXT,
                    created_at INTEGER DEFAULT (strftime('%s','now'))
                )
            """);

            st.execute("""
                CREATE TABLE IF NOT EXISTS clipboard_history (
                    id         INTEGER PRIMARY KEY AUTOINCREMENT,
                    content    TEXT NOT NULL,
                    copied_at  INTEGER DEFAULT (strftime('%s','now'))
                )
            """);

            st.execute("""
                CREATE TABLE IF NOT EXISTS sessions (
                    id          INTEGER PRIMARY KEY AUTOINCREMENT,
                    tab_index   INTEGER NOT NULL,
                    file_path   TEXT,
                    title       TEXT,
                    content     TEXT,
                    caret_pos   INTEGER DEFAULT 0,
                    saved_at    INTEGER DEFAULT (strftime('%s','now'))
                )
            """);

            insertDefaultSnippets();
        } catch (SQLException e) {
            LOG.log(Level.SEVERE, "Schema init failed", e);
        }
    }

    private void insertDefaultSnippets() throws SQLException {
        int count = 0;
        try (Statement st = connection.createStatement();
             ResultSet rs = st.executeQuery("SELECT COUNT(*) FROM snippets")) {
            if (rs.next()) count = rs.getInt(1);
        }
        if (count > 0) return;

        String[][] defaults = {
            {"Meeting Notes",   "Templates", "# Meeting Notes\nDate: \nAttendees:\n\n## Agenda\n1. \n\n## Action Items\n- "},
            {"Todo List",       "Templates", "# Todo List\n\n## Today\n- [ ] \n- [ ] \n\n## This Week\n- [ ] \n"},
            {"Journal Entry",   "Templates", "# Journal — \n\n## Today's Goals\n\n## What Happened\n\n## Reflection\n"},
            {"Main Method",     "Java",      "public static void main(String[] args) {\n    \n}"},
            {"System.out",      "Java",      "System.out.println();"},
            {"Try-Catch",       "Java",      "try {\n    \n} catch (Exception e) {\n    e.printStackTrace();\n}"},
            {"HTML Template",   "HTML",      "<!DOCTYPE html>\n<html lang=\"en\">\n<head>\n    <meta charset=\"UTF-8\">\n    <title></title>\n</head>\n<body>\n    \n</body>\n</html>"},
            {"Python Main",     "Python",    "if __name__ == '__main__':\n    main()"},
            {"Shebang Python",  "Python",    "#!/usr/bin/env python3\n# -*- coding: utf-8 -*-\n\n"},
        };

        try (PreparedStatement ps = connection.prepareStatement(
                "INSERT INTO snippets (name, category, content) VALUES (?, ?, ?)")) {
            for (String[] s : defaults) {
                ps.setString(1, s[0]);
                ps.setString(2, s[1]);
                ps.setString(3, s[2]);
                ps.executeUpdate();
            }
        }
    }

    // ── Recent Files ──────────────────────────────────────────────────────────

    public void addRecentFile(String path) {
        try {
            // Upsert
            try (PreparedStatement ps = connection.prepareStatement(
                    "INSERT OR REPLACE INTO recent_files (path, opened_at) VALUES (?, strftime('%s','now'))")) {
                ps.setString(1, path);
                ps.executeUpdate();
            }
            // Trim to last 50
            try (Statement st = connection.createStatement()) {
                st.execute("""
                    DELETE FROM recent_files WHERE id NOT IN (
                        SELECT id FROM recent_files ORDER BY opened_at DESC LIMIT 50
                    )
                """);
            }
        } catch (SQLException e) {
            LOG.log(Level.WARNING, "addRecentFile failed", e);
        }
    }

    public List<String> getRecentFiles() {
        List<String> list = new ArrayList<>();
        try (Statement st = connection.createStatement();
             ResultSet rs = st.executeQuery(
                     "SELECT path FROM recent_files ORDER BY opened_at DESC LIMIT 50")) {
            while (rs.next()) list.add(rs.getString("path"));
        } catch (SQLException e) {
            LOG.log(Level.WARNING, "getRecentFiles failed", e);
        }
        return list;
    }

    public void clearRecentFiles() {
        try (Statement st = connection.createStatement()) {
            st.execute("DELETE FROM recent_files");
        } catch (SQLException e) {
            LOG.log(Level.WARNING, "clearRecentFiles failed", e);
        }
    }

    // ── Bookmarks ─────────────────────────────────────────────────────────────

    public void addBookmark(BookmarkModel bm) {
        try (PreparedStatement ps = connection.prepareStatement(
                "INSERT OR REPLACE INTO bookmarks (file_path, line_number, label) VALUES (?, ?, ?)")) {
            ps.setString(1, bm.getFilePath());
            ps.setInt(2, bm.getLineNumber());
            ps.setString(3, bm.getLabel());
            ps.executeUpdate();
        } catch (SQLException e) {
            LOG.log(Level.WARNING, "addBookmark failed", e);
        }
    }

    public void removeBookmark(String filePath, int lineNumber) {
        try (PreparedStatement ps = connection.prepareStatement(
                "DELETE FROM bookmarks WHERE file_path=? AND line_number=?")) {
            ps.setString(1, filePath);
            ps.setInt(2, lineNumber);
            ps.executeUpdate();
        } catch (SQLException e) {
            LOG.log(Level.WARNING, "removeBookmark failed", e);
        }
    }

    public List<BookmarkModel> getBookmarks(String filePath) {
        List<BookmarkModel> list = new ArrayList<>();
        try (PreparedStatement ps = connection.prepareStatement(
                "SELECT * FROM bookmarks WHERE file_path=? ORDER BY line_number")) {
            ps.setString(1, filePath);
            ResultSet rs = ps.executeQuery();
            while (rs.next()) {
                BookmarkModel bm = new BookmarkModel();
                bm.setId(rs.getInt("id"));
                bm.setFilePath(rs.getString("file_path"));
                bm.setLineNumber(rs.getInt("line_number"));
                bm.setLabel(rs.getString("label"));
                bm.setCreatedAt(rs.getLong("created_at"));
                list.add(bm);
            }
        } catch (SQLException e) {
            LOG.log(Level.WARNING, "getBookmarks failed", e);
        }
        return list;
    }

    public boolean isBookmarked(String filePath, int lineNumber) {
        try (PreparedStatement ps = connection.prepareStatement(
                "SELECT COUNT(*) FROM bookmarks WHERE file_path=? AND line_number=?")) {
            ps.setString(1, filePath);
            ps.setInt(2, lineNumber);
            ResultSet rs = ps.executeQuery();
            return rs.next() && rs.getInt(1) > 0;
        } catch (SQLException e) {
            return false;
        }
    }

    // ── Snippets ──────────────────────────────────────────────────────────────

    public List<SnippetModel> getSnippets() {
        List<SnippetModel> list = new ArrayList<>();
        try (Statement st = connection.createStatement();
             ResultSet rs = st.executeQuery("SELECT * FROM snippets ORDER BY category, name")) {
            while (rs.next()) {
                SnippetModel s = new SnippetModel();
                s.setId(rs.getInt("id"));
                s.setName(rs.getString("name"));
                s.setCategory(rs.getString("category"));
                s.setContent(rs.getString("content"));
                s.setShortcut(rs.getString("shortcut"));
                s.setCreatedAt(rs.getLong("created_at"));
                list.add(s);
            }
        } catch (SQLException e) {
            LOG.log(Level.WARNING, "getSnippets failed", e);
        }
        return list;
    }

    public void addSnippet(SnippetModel s) {
        try (PreparedStatement ps = connection.prepareStatement(
                "INSERT INTO snippets (name, category, content, shortcut) VALUES (?, ?, ?, ?)")) {
            ps.setString(1, s.getName());
            ps.setString(2, s.getCategory());
            ps.setString(3, s.getContent());
            ps.setString(4, s.getShortcut());
            ps.executeUpdate();
        } catch (SQLException e) {
            LOG.log(Level.WARNING, "addSnippet failed", e);
        }
    }

    public void deleteSnippet(int id) {
        try (PreparedStatement ps = connection.prepareStatement(
                "DELETE FROM snippets WHERE id=?")) {
            ps.setInt(1, id);
            ps.executeUpdate();
        } catch (SQLException e) {
            LOG.log(Level.WARNING, "deleteSnippet failed", e);
        }
    }

    // ── Clipboard History ─────────────────────────────────────────────────────

    public void addClipboardEntry(String content) {
        if (content == null || content.isBlank()) return;
        try {
            try (PreparedStatement ps = connection.prepareStatement(
                    "DELETE FROM clipboard_history WHERE content=?")) {
                ps.setString(1, content);
                ps.executeUpdate();
            }
            try (PreparedStatement ps = connection.prepareStatement(
                    "INSERT INTO clipboard_history (content) VALUES (?)")) {
                ps.setString(1, content);
                ps.executeUpdate();
            }
            // Keep last 100
            try (Statement st = connection.createStatement()) {
                st.execute("""
                    DELETE FROM clipboard_history WHERE id NOT IN (
                        SELECT id FROM clipboard_history ORDER BY copied_at DESC LIMIT 100
                    )
                """);
            }
        } catch (SQLException e) {
            LOG.log(Level.WARNING, "addClipboardEntry failed", e);
        }
    }

    public List<String> getClipboardHistory() {
        List<String> list = new ArrayList<>();
        try (Statement st = connection.createStatement();
             ResultSet rs = st.executeQuery(
                     "SELECT content FROM clipboard_history ORDER BY copied_at DESC LIMIT 100")) {
            while (rs.next()) list.add(rs.getString("content"));
        } catch (SQLException e) {
            LOG.log(Level.WARNING, "getClipboardHistory failed", e);
        }
        return list;
    }

    public void clearClipboardHistory() {
        try (Statement st = connection.createStatement()) {
            st.execute("DELETE FROM clipboard_history");
        } catch (SQLException e) {
            LOG.log(Level.WARNING, "clearClipboardHistory failed", e);
        }
    }

    // ── Session ───────────────────────────────────────────────────────────────

    public void saveSession(List<SessionEntry> entries) {
        try (Statement st = connection.createStatement()) {
            st.execute("DELETE FROM sessions");
        } catch (SQLException e) {
            LOG.log(Level.WARNING, "clearSession failed", e);
        }
        try (PreparedStatement ps = connection.prepareStatement(
                "INSERT INTO sessions (tab_index, file_path, title, content, caret_pos) VALUES (?,?,?,?,?)")) {
            for (SessionEntry e : entries) {
                ps.setInt(1, e.tabIndex);
                ps.setString(2, e.filePath);
                ps.setString(3, e.title);
                ps.setString(4, e.content);
                ps.setInt(5, e.caretPos);
                ps.executeUpdate();
            }
        } catch (SQLException e) {
            LOG.log(Level.WARNING, "saveSession failed", e);
        }
    }

    public List<SessionEntry> loadSession() {
        List<SessionEntry> list = new ArrayList<>();
        try (Statement st = connection.createStatement();
             ResultSet rs = st.executeQuery(
                     "SELECT * FROM sessions ORDER BY tab_index")) {
            while (rs.next()) {
                SessionEntry e = new SessionEntry();
                e.tabIndex  = rs.getInt("tab_index");
                e.filePath  = rs.getString("file_path");
                e.title     = rs.getString("title");
                e.content   = rs.getString("content");
                e.caretPos  = rs.getInt("caret_pos");
                list.add(e);
            }
        } catch (SQLException e) {
            LOG.log(Level.WARNING, "loadSession failed", e);
        }
        return list;
    }

    // ── Nested DTO ────────────────────────────────────────────────────────────

    public static class SessionEntry {
        public int    tabIndex;
        public String filePath;
        public String title;
        public String content;
        public int    caretPos;
    }

    // ── Lifecycle ─────────────────────────────────────────────────────────────

    public void close() {
        try {
            if (connection != null && !connection.isClosed()) connection.close();
        } catch (SQLException e) {
            LOG.log(Level.WARNING, "Failed to close DB", e);
        }
    }
}
