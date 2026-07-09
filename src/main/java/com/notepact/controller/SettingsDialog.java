package com.notepact.controller;

import com.notepact.model.AppSettings;
import com.notepact.model.Theme;
import com.notepact.settings.SettingsManager;
import com.notepact.theme.ThemeManager;

import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.*;
import javafx.scene.layout.*;
import javafx.scene.text.Font;
import javafx.stage.Stage;

/**
 * Settings dialog for the application.
 * Covers: Editor, Font, Auto-save, Backup, Theme, and Keyboard shortcuts.
 */
public class SettingsDialog {

    private final Stage owner;
    private final AppSettings settings;
    private final Runnable onApply;

    public SettingsDialog(Stage owner, AppSettings settings, Runnable onApply) {
        this.owner    = owner;
        this.settings = settings;
        this.onApply  = onApply;
    }

    public void show() {
        Dialog<ButtonType> dlg = new Dialog<>();
        dlg.setTitle("Settings — Notepact");
        dlg.setHeaderText("Application Settings");
        dlg.getDialogPane().getButtonTypes().addAll(ButtonType.APPLY, ButtonType.CANCEL);
        dlg.setResizable(true);
        dlg.getDialogPane().setPrefSize(600, 520);

        TabPane tabs = new TabPane();
        tabs.setTabClosingPolicy(TabPane.TabClosingPolicy.UNAVAILABLE);
        tabs.getTabs().addAll(
            buildEditorTab(),
            buildFontTab(),
            buildAutoSaveTab(),
            buildBackupTab(),
            buildThemeTab(),
            buildShortcutsTab()
        );

        dlg.getDialogPane().setContent(tabs);

        // Apply theme
        javafx.scene.Scene sc = ThemeManager.getInstance().getScene();
        if (sc != null) dlg.getDialogPane().getScene().getStylesheets().addAll(sc.getStylesheets());

        dlg.showAndWait().ifPresent(bt -> {
            if (bt == ButtonType.APPLY) {
                SettingsManager.getInstance().save();
                if (onApply != null) onApply.run();
            }
        });
    }

    // ── Editor Tab ────────────────────────────────────────────────────────────

    private Tab buildEditorTab() {
        GridPane grid = settingsGrid();
        int row = 0;

        // Word wrap
        CheckBox cbWordWrap = new CheckBox("Enable Word Wrap");
        cbWordWrap.setSelected(settings.isWordWrap());
        cbWordWrap.setOnAction(e -> settings.setWordWrap(cbWordWrap.isSelected()));
        grid.add(new Label("Word Wrap:"), 0, row);
        grid.add(cbWordWrap, 1, row++);

        // Line numbers
        CheckBox cbLineNums = new CheckBox("Show Line Numbers");
        cbLineNums.setSelected(settings.isShowLineNumbers());
        cbLineNums.setOnAction(e -> settings.setShowLineNumbers(cbLineNums.isSelected()));
        grid.add(new Label("Line Numbers:"), 0, row);
        grid.add(cbLineNums, 1, row++);

        // Active line highlight
        CheckBox cbActiveLine = new CheckBox("Highlight Active Line");
        cbActiveLine.setSelected(settings.isHighlightActiveLine());
        cbActiveLine.setOnAction(e -> settings.setHighlightActiveLine(cbActiveLine.isSelected()));
        grid.add(new Label("Active Line:"), 0, row);
        grid.add(cbActiveLine, 1, row++);

        // Tab size
        Spinner<Integer> tabSize = new Spinner<>(1, 16, settings.getTabSize());
        tabSize.valueProperty().addListener((obs, o, n) -> settings.setTabSize(n));
        grid.add(new Label("Tab Size:"), 0, row);
        grid.add(tabSize, 1, row++);

        // Insert spaces
        CheckBox cbSpaces = new CheckBox("Insert Spaces (instead of tabs)");
        cbSpaces.setSelected(settings.isInsertSpaces());
        cbSpaces.setOnAction(e -> settings.setInsertSpaces(cbSpaces.isSelected()));
        grid.add(new Label("Use Spaces:"), 0, row);
        grid.add(cbSpaces, 1, row++);

        // Encoding
        ComboBox<String> cbEnc = new ComboBox<>();
        cbEnc.getItems().addAll("UTF-8", "UTF-16", "ISO-8859-1", "US-ASCII", "Windows-1252");
        cbEnc.setValue(settings.getDefaultEncoding());
        cbEnc.valueProperty().addListener((obs, o, n) -> settings.setDefaultEncoding(n));
        grid.add(new Label("Default Encoding:"), 0, row);
        grid.add(cbEnc, 1, row++);

        return new Tab("📝 Editor", new ScrollPane(grid));
    }

    // ── Font Tab ──────────────────────────────────────────────────────────────

    private Tab buildFontTab() {
        GridPane grid = settingsGrid();
        int row = 0;

        // Font family
        ComboBox<String> cbFont = new ComboBox<>();
        cbFont.getItems().addAll("Consolas", "Courier New", "Monaco", "Fira Code",
                "JetBrains Mono", "Source Code Pro", "Inconsolata", "Hack",
                "Cascadia Code", "SF Mono", "Menlo", "DejaVu Sans Mono");
        cbFont.setValue(settings.getFontFamily());
        cbFont.valueProperty().addListener((obs, o, n) -> settings.setFontFamily(n));
        grid.add(new Label("Font Family:"), 0, row);
        grid.add(cbFont, 1, row++);

        // Font size
        Spinner<Integer> spSize = new Spinner<>(6, 72, settings.getFontSize());
        spSize.valueProperty().addListener((obs, o, n) -> settings.setFontSize(n));
        grid.add(new Label("Font Size:"), 0, row);
        grid.add(spSize, 1, row++);

        // Line height
        Spinner<Double> spLH = new Spinner<>(1.0, 3.0, settings.getLineHeight(), 0.1);
        spLH.valueProperty().addListener((obs, o, n) -> settings.setLineHeight(n));
        grid.add(new Label("Line Height:"), 0, row);
        grid.add(spLH, 1, row++);

        // Preview
        Label lblPreview = new Label("The quick brown fox jumps over the lazy dog\n0123456789 {}[]()");
        lblPreview.setFont(Font.font(settings.getFontFamily(), settings.getFontSize()));
        lblPreview.setStyle("-fx-border-color: #555; -fx-padding: 10; -fx-background-color: #1a1a1a; -fx-text-fill: #d4d4d4;");
        cbFont.valueProperty().addListener((obs, o, n) ->
            lblPreview.setFont(Font.font(n, spSize.getValue())));
        spSize.valueProperty().addListener((obs, o, n) ->
            lblPreview.setFont(Font.font(cbFont.getValue(), n)));

        grid.add(new Label("Preview:"), 0, row);
        grid.add(lblPreview, 1, row++);

        return new Tab("🔤 Font", new ScrollPane(grid));
    }

    // ── Auto-Save Tab ─────────────────────────────────────────────────────────

    private Tab buildAutoSaveTab() {
        GridPane grid = settingsGrid();
        int row = 0;

        CheckBox cbEnable = new CheckBox("Enable Auto-Save");
        cbEnable.setSelected(settings.isAutoSaveEnabled());
        cbEnable.setOnAction(e -> settings.setAutoSaveEnabled(cbEnable.isSelected()));
        grid.add(new Label("Auto-Save:"), 0, row);
        grid.add(cbEnable, 1, row++);

        Label lblInterval = new Label("Interval:");
        Spinner<Integer> spInterval = new Spinner<>(10, 3600, settings.getAutoSaveInterval());
        spInterval.valueProperty().addListener((obs, o, n) -> settings.setAutoSaveInterval(n));
        grid.add(lblInterval, 0, row);
        grid.add(new HBox(5, spInterval, new Label("seconds")), 1, row++);

        grid.add(new Label("Presets:"), 0, row);
        HBox presets = new HBox(6,
            btn("30s", () -> spInterval.getValueFactory().setValue(30)),
            btn("1 min", () -> spInterval.getValueFactory().setValue(60)),
            btn("5 min", () -> spInterval.getValueFactory().setValue(300))
        );
        grid.add(presets, 1, row++);

        return new Tab("💾 Auto-Save", new ScrollPane(grid));
    }

    // ── Backup Tab ────────────────────────────────────────────────────────────

    private Tab buildBackupTab() {
        GridPane grid = settingsGrid();
        int row = 0;

        CheckBox cbBackup = new CheckBox("Enable Automatic Backups");
        cbBackup.setSelected(settings.isBackupEnabled());
        cbBackup.setOnAction(e -> settings.setBackupEnabled(cbBackup.isSelected()));
        grid.add(new Label("Backups:"), 0, row);
        grid.add(cbBackup, 1, row++);

        Spinner<Integer> spInterval = new Spinner<>(30, 7200, settings.getBackupInterval());
        spInterval.valueProperty().addListener((obs, o, n) -> settings.setBackupInterval(n));
        grid.add(new Label("Interval:"), 0, row);
        grid.add(new HBox(5, spInterval, new Label("seconds")), 1, row++);

        Spinner<Integer> spMaxBk = new Spinner<>(1, 100, settings.getMaxBackups());
        spMaxBk.valueProperty().addListener((obs, o, n) -> settings.setMaxBackups(n));
        grid.add(new Label("Max Versions:"), 0, row);
        grid.add(spMaxBk, 1, row++);

        String backupPath = System.getProperty("user.home") + "/.notepact/backups/";
        grid.add(new Label("Backup Folder:"), 0, row);
        Label lblPath = new Label(backupPath);
        lblPath.setStyle("-fx-text-fill: #4ec9b0; -fx-font-size: 11px;");
        grid.add(lblPath, 1, row++);

        return new Tab("🛡 Backup", new ScrollPane(grid));
    }

    // ── Theme Tab ─────────────────────────────────────────────────────────────

    private Tab buildThemeTab() {
        GridPane grid = settingsGrid();
        int row = 0;

        ToggleGroup tg = new ToggleGroup();
        grid.add(new Label("Color Theme:"), 0, row++);

        for (Theme t : Theme.values()) {
            RadioButton rb = new RadioButton(t.getDisplayName());
            rb.setToggleGroup(tg);
            rb.setSelected(t.getCssKey().equals(settings.getTheme()));
            rb.setOnAction(e -> {
                settings.setTheme(t.getCssKey());
                ThemeManager.getInstance().applyTheme(t);
            });
            grid.add(rb, 1, row++);
        }

        return new Tab("🎨 Theme", new ScrollPane(grid));
    }

    // ── Shortcuts Tab ─────────────────────────────────────────────────────────

    private Tab buildShortcutsTab() {
        String text = """
            Ctrl+N          New File
            Ctrl+O          Open File
            Ctrl+S          Save
            Ctrl+Shift+S    Save As
            Ctrl+W          Close Tab
            Ctrl+P          Print

            Ctrl+Z          Undo
            Ctrl+Y          Redo
            Ctrl+X          Cut
            Ctrl+C          Copy
            Ctrl+V          Paste
            Ctrl+A          Select All

            Ctrl+F          Find
            Ctrl+H          Replace
            F3              Find Next
            Shift+F3        Find Previous
            F5              Insert Date/Time
            Escape          Close Find Bar

            Ctrl+D          Duplicate Line
            Ctrl+L          Delete Line
            Ctrl+J          Join Lines
            Alt+Up          Move Line Up
            Alt+Down        Move Line Down
            Shift+Alt+Up    Copy Line Up
            Shift+Alt+Down  Copy Line Down

            Ctrl+F2         Toggle Bookmark
            F2              Next Bookmark
            Shift+F2        Previous Bookmark

            Ctrl+=          Zoom In
            Ctrl+-          Zoom Out
            Ctrl+0          Reset Zoom
            Ctrl+]          Increase Font Size
            Ctrl+[          Decrease Font Size

            Ctrl+Tab        Next Tab
            Ctrl+Shift+Tab  Previous Tab
            Ctrl+T          New Tab
            Ctrl+,          Settings
            """;

        TextArea ta = new TextArea(text);
        ta.setEditable(false);
        ta.setFont(Font.font("Consolas", 12));

        return new Tab("⌨ Shortcuts", ta);
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private GridPane settingsGrid() {
        GridPane g = new GridPane();
        g.setHgap(16);
        g.setVgap(12);
        g.setPadding(new Insets(16));
        ColumnConstraints c0 = new ColumnConstraints(150);
        ColumnConstraints c1 = new ColumnConstraints(300);
        g.getColumnConstraints().addAll(c0, c1);
        return g;
    }

    private Button btn(String text, Runnable action) {
        Button b = new Button(text);
        b.setOnAction(e -> action.run());
        return b;
    }

}

