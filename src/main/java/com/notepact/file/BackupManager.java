package com.notepact.file;

import com.notepact.model.AppSettings;
import com.notepact.settings.SettingsManager;

import java.io.*;
import java.nio.file.*;
import java.text.SimpleDateFormat;
import java.util.*;
import java.util.concurrent.*;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Creates timestamped backup copies of documents.
 * Runs on a background scheduler. Keeps the last N versions per file.
 */
public class BackupManager {

    private static final Logger LOG = Logger.getLogger(BackupManager.class.getName());
    private static BackupManager instance;

    private final File backupDir;
    private final ScheduledExecutorService scheduler;
    private final SimpleDateFormat fmt = new SimpleDateFormat("yyyyMMdd_HHmmss");

    // ── Singleton ─────────────────────────────────────────────────────────────

    private BackupManager() {
        String home = System.getProperty("user.home");
        this.backupDir = new File(home, ".notepact/backups");
        backupDir.mkdirs();
        this.scheduler = Executors.newSingleThreadScheduledExecutor(r -> {
            Thread t = new Thread(r, "BackupManager");
            t.setDaemon(true);
            return t;
        });
    }

    public static synchronized BackupManager getInstance() {
        if (instance == null) instance = new BackupManager();
        return instance;
    }

    // ── Manual backup ─────────────────────────────────────────────────────────

    /**
     * Creates a backup of the given file immediately.
     */
    public void backup(File sourceFile, String content) {
        if (sourceFile == null) return;
        AppSettings s = SettingsManager.getInstance().getSettings();
        if (!s.isBackupEnabled()) return;

        scheduler.submit(() -> doBackup(sourceFile, content, s.getMaxBackups()));
    }

    private void doBackup(File source, String content, int maxBackups) {
        try {
            String safeName = source.getName().replaceAll("[^a-zA-Z0-9._-]", "_");
            File dir = new File(backupDir, safeName);
            dir.mkdirs();

            String timestamp = fmt.format(new Date());
            File backup = new File(dir, safeName + "." + timestamp + ".bak");

            Files.writeString(backup.toPath(), content, java.nio.charset.StandardCharsets.UTF_8);
            LOG.info("Backup created: " + backup.getAbsolutePath());

            pruneOldBackups(dir, maxBackups);
        } catch (Exception e) {
            LOG.log(Level.WARNING, "Backup failed", e);
        }
    }

    private void pruneOldBackups(File dir, int maxBackups) {
        File[] files = dir.listFiles((d, name) -> name.endsWith(".bak"));
        if (files == null || files.length <= maxBackups) return;
        Arrays.sort(files, Comparator.comparingLong(File::lastModified));
        int toDelete = files.length - maxBackups;
        for (int i = 0; i < toDelete; i++) {
            files[i].delete();
            LOG.fine("Deleted old backup: " + files[i].getName());
        }
    }

    // ── Scheduled backup ──────────────────────────────────────────────────────

    private ScheduledFuture<?> scheduledTask;

    public void startScheduler(Runnable backupAction) {
        stopScheduler();
        AppSettings s = SettingsManager.getInstance().getSettings();
        if (!s.isBackupEnabled()) return;

        long interval = s.getBackupInterval();
        scheduledTask = scheduler.scheduleAtFixedRate(() -> {
            try { backupAction.run(); }
            catch (Exception e) { LOG.log(Level.WARNING, "Scheduled backup error", e); }
        }, interval, interval, TimeUnit.SECONDS);

        LOG.info("Backup scheduler started (interval=" + interval + "s)");
    }

    public void stopScheduler() {
        if (scheduledTask != null) {
            scheduledTask.cancel(false);
            scheduledTask = null;
        }
    }

    // ── List backups ──────────────────────────────────────────────────────────

    public List<File> listBackups(File sourceFile) {
        String safeName = sourceFile.getName().replaceAll("[^a-zA-Z0-9._-]", "_");
        File dir = new File(backupDir, safeName);
        if (!dir.exists()) return Collections.emptyList();

        File[] files = dir.listFiles((d, name) -> name.endsWith(".bak"));
        if (files == null) return Collections.emptyList();
        Arrays.sort(files, Comparator.comparingLong(File::lastModified).reversed());
        return Arrays.asList(files);
    }

    public File getBackupDir() { return backupDir; }

    public void shutdown() {
        scheduler.shutdown();
    }
}
