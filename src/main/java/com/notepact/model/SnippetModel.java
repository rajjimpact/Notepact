package com.notepact.model;

/**
 * Represents a reusable text snippet stored in the database.
 */
public class SnippetModel {

    private int id;
    private String name;
    private String category;
    private String content;
    private String shortcut;
    private long createdAt;

    public SnippetModel() {
        this.createdAt = System.currentTimeMillis();
    }

    public SnippetModel(String name, String category, String content) {
        this();
        this.name = name;
        this.category = category;
        this.content = content;
    }

    // ── Getters & Setters ────────────────────────────────────────────────────

    public int getId() { return id; }
    public void setId(int id) { this.id = id; }

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }

    public String getCategory() { return category; }
    public void setCategory(String category) { this.category = category; }

    public String getContent() { return content; }
    public void setContent(String content) { this.content = content; }

    public String getShortcut() { return shortcut; }
    public void setShortcut(String shortcut) { this.shortcut = shortcut; }

    public long getCreatedAt() { return createdAt; }
    public void setCreatedAt(long createdAt) { this.createdAt = createdAt; }

    @Override
    public String toString() {
        return "[" + category + "] " + name;
    }
}
