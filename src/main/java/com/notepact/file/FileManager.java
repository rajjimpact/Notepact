package com.notepact.file;

import com.notepact.db.DatabaseManager;
import com.notepact.model.AppSettings;
import com.notepact.settings.SettingsManager;

import java.io.*;
import java.nio.charset.Charset;
import java.nio.file.*;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.function.Consumer;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Handles all file I/O with async support for large files.
 * Reads files using BufferedReader for memory efficiency.
 */
public class FileManager {

    private static final Logger LOG = Logger.getLogger(FileManager.class.getName());
    private static FileManager instance;
    private final ExecutorService executor = Executors.newCachedThreadPool(r -> {
        Thread t = new Thread(r, "FileIO");
        t.setDaemon(true);
        return t;
    });

    private FileManager() {}

    public static synchronized FileManager getInstance() {
        if (instance == null) instance = new FileManager();
        return instance;
    }

    // ── Read ──────────────────────────────────────────────────────────────────

    /**
     * Reads a file synchronously. Suitable for files < ~50 MB.
     */
    public String readSync(File file, String encoding) throws IOException {
        Charset charset = Charset.forName(encoding);
        StringBuilder sb = new StringBuilder((int) Math.min(file.length(), Integer.MAX_VALUE));
        try (BufferedReader reader = new BufferedReader(
                new InputStreamReader(new FileInputStream(file), charset), 65536)) {
            String line;
            boolean first = true;
            while ((line = reader.readLine()) != null) {
                if (!first) sb.append('\n');
                sb.append(line);
                first = false;
            }
        }
        return sb.toString();
    }

    /**
     * Reads a file asynchronously. Calls onSuccess or onError on the FX thread.
     */
    public void readAsync(File file, String encoding,
                          Consumer<String> onSuccess, Consumer<Exception> onError) {
        executor.submit(() -> {
            try {
                String content = readSync(file, encoding);
                javafx.application.Platform.runLater(() -> onSuccess.accept(content));
            } catch (Exception e) {
                javafx.application.Platform.runLater(() -> onError.accept(e));
            }
        });
    }

    // ── Write ─────────────────────────────────────────────────────────────────

    /**
     * Writes content to a file synchronously.
     */
    public void writeSync(File file, String content, String encoding) throws IOException {
        Charset charset = Charset.forName(encoding);
        // Write to temp file first for atomicity
        Path tmp = file.toPath().resolveSibling(file.getName() + ".tmp");
        try (BufferedWriter writer = new BufferedWriter(
                new OutputStreamWriter(new FileOutputStream(tmp.toFile()), charset), 65536)) {
            writer.write(content);
        }
        Files.move(tmp, file.toPath(), StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);

        // Record in recent files
        DatabaseManager.getInstance().addRecentFile(file.getAbsolutePath());
    }

    /**
     * Writes content asynchronously.
     */
    public void writeAsync(File file, String content, String encoding,
                           Runnable onSuccess, Consumer<Exception> onError) {
        executor.submit(() -> {
            try {
                writeSync(file, content, encoding);
                if (onSuccess != null)
                    javafx.application.Platform.runLater(onSuccess);
            } catch (Exception e) {
                if (onError != null)
                    javafx.application.Platform.runLater(() -> onError.accept(e));
            }
        });
    }

    // ── Supported Extensions ──────────────────────────────────────────────────

    public static final String[] SUPPORTED_EXTENSIONS = {
        "*.txt", "*.md", "*.log", "*.csv", "*.json", "*.xml",
        "*.java", "*.py", "*.cpp", "*.c", "*.h", "*.js", "*.ts",
        "*.html", "*.htm", "*.css", "*.sql", "*.sh", "*.bat",
        "*.yaml", "*.yml", "*.properties", "*.ini", "*.cfg",
        "*.gradle", "*.pom", "*.rb", "*.go", "*.rs", "*.kt",
        "*.swift", "*.php", "*.r", "*.scala", "*.pl"
    };

    public static final String FILTER_DESCRIPTION =
            "All Supported Files (txt, md, java, py, js, html, css, json, xml, sql...)";

    // ── File utilities ────────────────────────────────────────────────────────

    public static String getExtension(File file) {
        String name = file.getName();
        int dot = name.lastIndexOf('.');
        return (dot > 0) ? name.substring(dot + 1) : "";
    }

    public static long fileSizeBytes(File file) {
        return file.length();
    }

    public static String fileSizeFormatted(File file) {
        long bytes = file.length();
        if (bytes < 1024) return bytes + " B";
        if (bytes < 1024 * 1024) return String.format("%.1f KB", bytes / 1024.0);
        return String.format("%.2f MB", bytes / (1024.0 * 1024));
    }

    public void shutdown() {
        executor.shutdown();
    }
}
