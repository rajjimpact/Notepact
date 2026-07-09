package com.notepact.model;

/**
 * Represents a bookmark on a specific line in a document.
 */
public class BookmarkModel {

    private int id;
    private String filePath;
    private int lineNumber;
    private String label;
    private long createdAt;

    public BookmarkModel() {
        this.createdAt = System.currentTimeMillis();
    }

    public BookmarkModel(String filePath, int lineNumber, String label) {
        this();
        this.filePath = filePath;
        this.lineNumber = lineNumber;
        this.label = label;
    }

    // ── Getters & Setters ────────────────────────────────────────────────────

    public int getId() { return id; }
    public void setId(int id) { this.id = id; }

    public String getFilePath() { return filePath; }
    public void setFilePath(String filePath) { this.filePath = filePath; }

    public int getLineNumber() { return lineNumber; }
    public void setLineNumber(int lineNumber) { this.lineNumber = lineNumber; }

    public String getLabel() { return label; }
    public void setLabel(String label) { this.label = label; }

    public long getCreatedAt() { return createdAt; }
    public void setCreatedAt(long createdAt) { this.createdAt = createdAt; }

    @Override
    public String toString() {
        return "Line " + lineNumber + (label != null && !label.isEmpty() ? ": " + label : "");
    }
}
