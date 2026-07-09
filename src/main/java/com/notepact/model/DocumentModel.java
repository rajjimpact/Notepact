package com.notepact.model;

import javafx.beans.property.*;

import java.io.File;

/**
 * Represents the state of a single open document / editor tab.
 */
public class DocumentModel {

    private File file;
    private final StringProperty title     = new SimpleStringProperty("Untitled");
    private final BooleanProperty modified = new SimpleBooleanProperty(false);
    private final BooleanProperty readOnly = new SimpleBooleanProperty(false);
    private String encoding = "UTF-8";
    private String language = "Plain Text";
    private long   lastSavedAt = 0;
    private String unsavedContent = "";   // holds content when no file yet

    /** Unique ID for session recovery */
    private final String id;

    private static int counter = 1;

    public DocumentModel() {
        this.id = "doc-" + (counter++);
    }

    public DocumentModel(File file) {
        this();
        this.file = file;
        this.title.set(file.getName());
        this.language = detectLanguage(file.getName());
    }

    // ── Language detection ───────────────────────────────────────────────────

    public static String detectLanguage(String filename) {
        if (filename == null) return "Plain Text";
        String lower = filename.toLowerCase();
        if (lower.endsWith(".java"))       return "Java";
        if (lower.endsWith(".py"))         return "Python";
        if (lower.endsWith(".js"))         return "JavaScript";
        if (lower.endsWith(".ts"))         return "TypeScript";
        if (lower.endsWith(".html") || lower.endsWith(".htm")) return "HTML";
        if (lower.endsWith(".css"))        return "CSS";
        if (lower.endsWith(".xml"))        return "XML";
        if (lower.endsWith(".json"))       return "JSON";
        if (lower.endsWith(".sql"))        return "SQL";
        if (lower.endsWith(".md"))         return "Markdown";
        if (lower.endsWith(".cpp") || lower.endsWith(".h")) return "C++";
        if (lower.endsWith(".c"))          return "C";
        if (lower.endsWith(".sh"))         return "Shell";
        if (lower.endsWith(".yaml") || lower.endsWith(".yml")) return "YAML";
        if (lower.endsWith(".log"))        return "Log";
        if (lower.endsWith(".csv"))        return "CSV";
        return "Plain Text";
    }

    // ── Display title (appends * if modified) ────────────────────────────────

    public String getDisplayTitle() {
        return modified.get() ? title.get() + " ●" : title.get();
    }

    // ── Getters & Setters ────────────────────────────────────────────────────

    public String getId() { return id; }

    public File getFile() { return file; }
    public void setFile(File file) {
        this.file = file;
        if (file != null) {
            title.set(file.getName());
            language = detectLanguage(file.getName());
        }
    }

    public StringProperty titleProperty() { return title; }
    public String getTitle() { return title.get(); }
    public void setTitle(String t) { title.set(t); }

    public BooleanProperty modifiedProperty() { return modified; }
    public boolean isModified() { return modified.get(); }
    public void setModified(boolean m) { modified.set(m); }

    public BooleanProperty readOnlyProperty() { return readOnly; }
    public boolean isReadOnly() { return readOnly.get(); }
    public void setReadOnly(boolean r) { readOnly.set(r); }

    public String getEncoding() { return encoding; }
    public void setEncoding(String enc) { this.encoding = enc; }

    public String getLanguage() { return language; }
    public void setLanguage(String lang) { this.language = lang; }

    public long getLastSavedAt() { return lastSavedAt; }
    public void setLastSavedAt(long t) { this.lastSavedAt = t; }

    public String getUnsavedContent() { return unsavedContent; }
    public void setUnsavedContent(String c) { this.unsavedContent = c; }

    public boolean isNew() { return file == null; }

    @Override
    public String toString() {
        return getDisplayTitle();
    }
}
