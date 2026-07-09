package com.notepact.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

/**
 * Application-wide settings persisted to JSON.
 * Loaded at startup, saved on change.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public class AppSettings {

    // ── Editor ───────────────────────────────────────────────────────────────
    private String fontFamily     = "Consolas";
    private int    fontSize       = 14;
    private String fontWeight     = "Normal";
    private double lineHeight     = 1.5;
    private double letterSpacing  = 0.0;
    private boolean wordWrap      = false;
    private boolean showLineNumbers = true;
    private boolean highlightActiveLine = true;
    private boolean showWhitespace = false;
    private boolean showIndentGuides = true;
    private int tabSize           = 4;
    private boolean insertSpaces  = true;
    private int zoomLevel         = 100;   // percent

    // ── Theme ────────────────────────────────────────────────────────────────
    private String theme = Theme.DARK.getCssKey();

    // ── Auto Save ────────────────────────────────────────────────────────────
    private boolean autoSaveEnabled  = true;
    private int     autoSaveInterval = 60;  // seconds

    // ── Backup ───────────────────────────────────────────────────────────────
    private boolean backupEnabled  = true;
    private int     backupInterval = 300; // seconds (5 min)
    private int     maxBackups     = 20;

    // ── UI ───────────────────────────────────────────────────────────────────
    private boolean showToolbar  = true;
    private boolean showSidebar  = true;
    private boolean showStatusBar = true;
    private double sidebarWidth  = 240.0;

    // ── Window ───────────────────────────────────────────────────────────────
    private double windowWidth  = 1280.0;
    private double windowHeight = 800.0;
    private double windowX      = -1;
    private double windowY      = -1;
    private boolean maximized   = false;

    // ── Encoding ─────────────────────────────────────────────────────────────
    private String defaultEncoding = "UTF-8";

    // ── Search ────────────────────────────────────────────────────────────────
    private boolean searchCaseSensitive = false;
    private boolean searchWholeWord     = false;
    private boolean searchRegex         = false;

    // ── Getters & Setters ────────────────────────────────────────────────────

    public String getFontFamily() { return fontFamily; }
    public void setFontFamily(String v) { this.fontFamily = v; }

    public int getFontSize() { return fontSize; }
    public void setFontSize(int v) { this.fontSize = v; }

    public String getFontWeight() { return fontWeight; }
    public void setFontWeight(String v) { this.fontWeight = v; }

    public double getLineHeight() { return lineHeight; }
    public void setLineHeight(double v) { this.lineHeight = v; }

    public double getLetterSpacing() { return letterSpacing; }
    public void setLetterSpacing(double v) { this.letterSpacing = v; }

    public boolean isWordWrap() { return wordWrap; }
    public void setWordWrap(boolean v) { this.wordWrap = v; }

    public boolean isShowLineNumbers() { return showLineNumbers; }
    public void setShowLineNumbers(boolean v) { this.showLineNumbers = v; }

    public boolean isHighlightActiveLine() { return highlightActiveLine; }
    public void setHighlightActiveLine(boolean v) { this.highlightActiveLine = v; }

    public boolean isShowWhitespace() { return showWhitespace; }
    public void setShowWhitespace(boolean v) { this.showWhitespace = v; }

    public boolean isShowIndentGuides() { return showIndentGuides; }
    public void setShowIndentGuides(boolean v) { this.showIndentGuides = v; }

    public int getTabSize() { return tabSize; }
    public void setTabSize(int v) { this.tabSize = v; }

    public boolean isInsertSpaces() { return insertSpaces; }
    public void setInsertSpaces(boolean v) { this.insertSpaces = v; }

    public int getZoomLevel() { return zoomLevel; }
    public void setZoomLevel(int v) { this.zoomLevel = v; }

    public String getTheme() { return theme; }
    public void setTheme(String v) { this.theme = v; }

    public boolean isAutoSaveEnabled() { return autoSaveEnabled; }
    public void setAutoSaveEnabled(boolean v) { this.autoSaveEnabled = v; }

    public int getAutoSaveInterval() { return autoSaveInterval; }
    public void setAutoSaveInterval(int v) { this.autoSaveInterval = v; }

    public boolean isBackupEnabled() { return backupEnabled; }
    public void setBackupEnabled(boolean v) { this.backupEnabled = v; }

    public int getBackupInterval() { return backupInterval; }
    public void setBackupInterval(int v) { this.backupInterval = v; }

    public int getMaxBackups() { return maxBackups; }
    public void setMaxBackups(int v) { this.maxBackups = v; }

    public boolean isShowToolbar() { return showToolbar; }
    public void setShowToolbar(boolean v) { this.showToolbar = v; }

    public boolean isShowSidebar() { return showSidebar; }
    public void setShowSidebar(boolean v) { this.showSidebar = v; }

    public boolean isShowStatusBar() { return showStatusBar; }
    public void setShowStatusBar(boolean v) { this.showStatusBar = v; }

    public double getSidebarWidth() { return sidebarWidth; }
    public void setSidebarWidth(double v) { this.sidebarWidth = v; }

    public double getWindowWidth() { return windowWidth; }
    public void setWindowWidth(double v) { this.windowWidth = v; }

    public double getWindowHeight() { return windowHeight; }
    public void setWindowHeight(double v) { this.windowHeight = v; }

    public double getWindowX() { return windowX; }
    public void setWindowX(double v) { this.windowX = v; }

    public double getWindowY() { return windowY; }
    public void setWindowY(double v) { this.windowY = v; }

    public boolean isMaximized() { return maximized; }
    public void setMaximized(boolean v) { this.maximized = v; }

    public String getDefaultEncoding() { return defaultEncoding; }
    public void setDefaultEncoding(String v) { this.defaultEncoding = v; }

    public boolean isSearchCaseSensitive() { return searchCaseSensitive; }
    public void setSearchCaseSensitive(boolean v) { this.searchCaseSensitive = v; }

    public boolean isSearchWholeWord() { return searchWholeWord; }
    public void setSearchWholeWord(boolean v) { this.searchWholeWord = v; }

    public boolean isSearchRegex() { return searchRegex; }
    public void setSearchRegex(boolean v) { this.searchRegex = v; }
}
