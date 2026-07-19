package com.notepact.controller;

import com.notepact.db.DatabaseManager;
import com.notepact.db.DatabaseManager.SessionEntry;
import com.notepact.editor.AutoSaveManager;
import com.notepact.editor.EditorPane;
import com.notepact.export.ExportManager;
import com.notepact.file.BackupManager;
import com.notepact.file.FileManager;
import com.notepact.file.RecentFilesManager;
import com.notepact.file.SessionManager;
import com.notepact.model.*;
import com.notepact.search.SearchEngine;
import com.notepact.search.SearchResult;
import com.notepact.settings.SettingsManager;
import com.notepact.theme.ThemeManager;
import com.notepact.util.TextUtils;

import javafx.application.Platform;
import javafx.collections.*;
import javafx.geometry.*;
import javafx.scene.*;
import javafx.scene.control.*;
import javafx.scene.control.Alert.AlertType;
import javafx.scene.input.*;
import javafx.scene.layout.*;
import javafx.scene.paint.Color;
import javafx.scene.text.*;
import javafx.stage.*;

import java.io.File;
import java.util.*;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Main application controller. Builds the entire UI programmatically
 * and wires all components together.
 *
 * Layout:
 * <pre>
 *   ┌──────────────────────────────────────────┐
 *   │  MenuBar                                  │
 *   │  ToolBar                                  │
 *   ├──────────┬───────────────────────────────┤
 *   │ Sidebar  │  TabPane  (editor tabs)        │
 *   │          │  FindBar  (hidden by default)  │
 *   ├──────────┴───────────────────────────────┤
 *   │  StatusBar                                │
 *   └──────────────────────────────────────────┘
 * </pre>
 */
public class MainController {

    private static final Logger LOG = Logger.getLogger(MainController.class.getName());

    // ── Scene graph roots ─────────────────────────────────────────────────────

    private final Stage stage;
    private VBox root;
    private SplitPane mainSplit;
    private VBox sidebar;
    private BorderPane editorArea;

    // ── Toolbar buttons (need references for enable/disable) ──────────────────

    private Button btnNew, btnOpen, btnSave, btnSaveAs;
    private Button btnUndo, btnRedo, btnCut, btnCopy, btnPaste;
    private Button btnFind, btnReplace;
    private ToggleButton btnThemeToggle;
    private Label lblZoom;

    // ── Tab management ────────────────────────────────────────────────────────

    private TabPane tabPane;
    private final ObservableList<TabEntry> tabs = FXCollections.observableArrayList();
    private int untitledCounter = 1;

    // ── Find bar ──────────────────────────────────────────────────────────────

    private FindReplaceBar findBar;
    private boolean findBarVisible = false;

    // ── Status bar ────────────────────────────────────────────────────────────

    private Label lblLine, lblCol, lblWords, lblChars, lblEncoding, lblLang, lblZoomStatus, lblModified;
    private Label lblAutoSave;

    // ── Sidebar panels ────────────────────────────────────────────────────────

    private ListView<String> recentFilesList;
    private ListView<BookmarkModel> bookmarkList;
    private ObservableList<BookmarkModel> currentBookmarks = FXCollections.observableArrayList();
    private TreeView<String> fileExplorer;
    private ListView<SnippetModel> snippetList;

    // ── Notes panel ───────────────────────────────────────────────────────────

    private TextArea notesArea;

    // ── Settings ──────────────────────────────────────────────────────────────

    private AppSettings settings;
    private int currentZoom = 100;

    // ── Constructor ───────────────────────────────────────────────────────────

    public MainController(Stage stage) {
        this.stage   = stage;
        this.settings = SettingsManager.getInstance().getSettings();
        this.currentZoom = settings.getZoomLevel();
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // BUILD UI
    // ═══════════════════════════════════════════════════════════════════════════

    public Scene buildScene() {
        root = new VBox();
        root.getStyleClass().add("root-container");

        // 1. Menu bar
        MenuBar menuBar = buildMenuBar();
        menuBar.getStyleClass().add("menu-bar");

        // 2. Toolbar
        ToolBar toolBar = buildToolBar();
        toolBar.getStyleClass().add("tool-bar");

        // 3. Main split (sidebar | editor)
        mainSplit = new SplitPane();
        mainSplit.setDividerPositions(0.18);
        mainSplit.getStyleClass().add("main-split");
        VBox.setVgrow(mainSplit, Priority.ALWAYS);

        sidebar    = buildSidebar();
        editorArea = buildEditorArea();

        if (settings.isShowSidebar()) {
            mainSplit.getItems().addAll(sidebar, editorArea);
        } else {
            mainSplit.getItems().add(editorArea);
        }

        // 4. Status bar
        HBox statusBar = buildStatusBar();
        statusBar.getStyleClass().add("status-bar");

        root.getChildren().addAll(menuBar, toolBar, mainSplit, statusBar);

        // Visibility toggles
        toolBar.setVisible(settings.isShowToolbar());
        toolBar.setManaged(settings.isShowToolbar());
        statusBar.setVisible(settings.isShowStatusBar());
        statusBar.setManaged(settings.isShowStatusBar());

        Scene scene = new Scene(root, settings.getWindowWidth(), settings.getWindowHeight());

        // Apply theme
        ThemeManager.getInstance().initialize(scene,
                Theme.valueOf(settings.getTheme().toUpperCase().replace("-","_")));

        // Global keyboard shortcuts
        scene.addEventFilter(KeyEvent.KEY_PRESSED, this::handleGlobalKeys);

        return scene;
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // MENU BAR
    // ═══════════════════════════════════════════════════════════════════════════

    private MenuBar buildMenuBar() {
        MenuBar bar = new MenuBar();

        bar.getMenus().addAll(
            buildFileMenu(),
            buildEditMenu(),
            buildViewMenu(),
            buildFormatMenu(),
            buildToolsMenu(),
            buildWindowMenu(),
            buildHelpMenu()
        );
        return bar;
    }

    // ── File Menu ─────────────────────────────────────────────────────────────

    private Menu buildFileMenu() {
        Menu m = new Menu("File");

        MenuItem miNew     = menuItem("New",      "Ctrl+N",  () -> newFile());
        MenuItem miNewWin  = menuItem("New Window","Ctrl+Shift+N", this::newWindow);
        MenuItem miOpen    = menuItem("Open...",  "Ctrl+O",  this::openFile);
        MenuItem miSave    = menuItem("Save",     "Ctrl+S",  this::saveFile);
        MenuItem miSaveAs  = menuItem("Save As..","Ctrl+Shift+S", this::saveFileAs);
        MenuItem miSaveAll = menuItem("Save All", "Ctrl+Alt+S", this::saveAllFiles);

        Menu miRecentMenu = new Menu("Recent Files");
        miRecentMenu.setOnShowing(e -> populateRecentMenu(miRecentMenu));

        MenuItem miClearRecent = menuItem("Clear Recent Files", null, () -> {
            RecentFilesManager.getInstance().clear();
        });

        SeparatorMenuItem sep1 = new SeparatorMenuItem();
        SeparatorMenuItem sep2 = new SeparatorMenuItem();
        SeparatorMenuItem sep3 = new SeparatorMenuItem();

        Menu miExport = new Menu("Export As");
        miExport.getItems().addAll(
            menuItem("Plain Text (.txt)",     null, () -> exportAs("txt")),
            menuItem("HTML (.html)",           null, () -> exportAs("html")),
            menuItem("Markdown (.md)",         null, () -> exportAs("md")),
            menuItem("PDF (.pdf)",             null, () -> exportAs("pdf"))
        );

        MenuItem miPrint     = menuItem("Print...",         "Ctrl+P",     this::printDocument);
        MenuItem miClose     = menuItem("Close Tab",        "Ctrl+W",     this::closeCurrentTab);
        MenuItem miCloseAll  = menuItem("Close All Tabs",   "Ctrl+Shift+W", this::closeAllTabs);
        MenuItem miExit      = menuItem("Exit",             "Alt+F4",     this::exitApp);

        m.getItems().addAll(
            miNew, miNewWin, sep1,
            miOpen, miRecentMenu, sep2,
            miSave, miSaveAs, miSaveAll, sep3,
            miExport, new SeparatorMenuItem(),
            miPrint, new SeparatorMenuItem(),
            miClose, miCloseAll, new SeparatorMenuItem(),
            miClearRecent, new SeparatorMenuItem(),
            miExit
        );
        return m;
    }

    // ── Edit Menu ─────────────────────────────────────────────────────────────

    private Menu buildEditMenu() {
        Menu m = new Menu("Edit");

        m.getItems().addAll(
            menuItem("Undo",       "Ctrl+Z",       () -> activeEditor().ifPresent(EditorPane::undo)),
            menuItem("Redo",       "Ctrl+Y",       () -> activeEditor().ifPresent(EditorPane::redo)),
            new SeparatorMenuItem(),
            menuItem("Cut",        "Ctrl+X",       () -> activeEditor().ifPresent(EditorPane::cut)),
            menuItem("Copy",       "Ctrl+C",       () -> activeEditor().ifPresent(EditorPane::copy)),
            menuItem("Paste",      "Ctrl+V",       () -> activeEditor().ifPresent(EditorPane::paste)),
            menuItem("Select All", "Ctrl+A",       () -> activeEditor().ifPresent(EditorPane::selectAll)),
            new SeparatorMenuItem(),
            menuItem("Find...",    "Ctrl+F",       this::showFind),
            menuItem("Find Next",  "F3",           this::findNext),
            menuItem("Find Prev",  "Shift+F3",     this::findPrevious),
            menuItem("Replace...", "Ctrl+H",       this::showReplace),
            new SeparatorMenuItem(),
            menuItem("Duplicate Line",  "Ctrl+D",  () -> activeEditor().ifPresent(EditorPane::duplicateLine)),
            menuItem("Delete Line",     "Ctrl+L",  () -> activeEditor().ifPresent(EditorPane::deleteLine)),
            menuItem("Join Lines",      "Ctrl+J",  () -> activeEditor().ifPresent(EditorPane::joinSelectedLines)),
            new SeparatorMenuItem(),
            buildDateTimeMenu(),
            new SeparatorMenuItem(),
            menuItem("Settings...",     "Ctrl+,",  this::showSettings)
        );
        return m;
    }

    private Menu buildDateTimeMenu() {
        Menu m = new Menu("Insert Date/Time  (F5)");
        m.getItems().addAll(
            menuItem("DD/MM/YYYY",           null, () -> insertText(TextUtils.getDateTime_DDMMYYYY())),
            menuItem("MM/DD/YYYY",           null, () -> insertText(TextUtils.getDateTime_MMDDYYYY())),
            menuItem("YYYY-MM-DD",           null, () -> insertText(TextUtils.getDateTime_ISO())),
            menuItem("Time (24h)",           null, () -> insertText(TextUtils.getDateTime_Time24())),
            menuItem("Time (12h AM/PM)",     null, () -> insertText(TextUtils.getDateTime_Time12())),
            menuItem("Full DateTime",        null, () -> insertText(TextUtils.getDateTime_Full())),
            menuItem("ISO 8601 Timestamp",   null, () -> insertText(TextUtils.getDateTime_Timestamp()))
        );
        return m;
    }

    // ── View Menu ─────────────────────────────────────────────────────────────

    private Menu buildViewMenu() {
        Menu m = new Menu("View");

        CheckMenuItem miSidebar   = new CheckMenuItem("Show Sidebar");
        CheckMenuItem miToolbar   = new CheckMenuItem("Show Toolbar");
        CheckMenuItem miStatusBar = new CheckMenuItem("Show Status Bar");
        CheckMenuItem miLineNums  = new CheckMenuItem("Show Line Numbers");
        CheckMenuItem miWordWrap  = new CheckMenuItem("Word Wrap");

        miSidebar.setSelected(settings.isShowSidebar());
        miToolbar.setSelected(settings.isShowToolbar());
        miStatusBar.setSelected(settings.isShowStatusBar());
        miWordWrap.setSelected(settings.isWordWrap());
        miLineNums.setSelected(settings.isShowLineNumbers());

        miWordWrap.setOnAction(e -> {
            settings.setWordWrap(miWordWrap.isSelected());
            activeEditor().ifPresent(ed -> ed.setWordWrap(miWordWrap.isSelected()));
        });

        m.getItems().addAll(
            miSidebar, miToolbar, miStatusBar,
            new SeparatorMenuItem(),
            miLineNums, miWordWrap,
            new SeparatorMenuItem(),
            buildThemeMenu(),
            new SeparatorMenuItem(),
            menuItem("Zoom In",    null,  this::zoomIn),
            menuItem("Zoom Out",   null,  this::zoomOut),
            menuItem("Reset Zoom", null,  this::zoomReset)
        );
        return m;
    }

    private Menu buildThemeMenu() {
        Menu m = new Menu("Theme");
        ToggleGroup tg = new ToggleGroup();
        String current = settings.getTheme();
        for (Theme t : Theme.values()) {
            RadioMenuItem ri = new RadioMenuItem(t.getDisplayName());
            ri.setToggleGroup(tg);
            ri.setSelected(t.getCssKey().equals(current));
            ri.setOnAction(e -> {
                ThemeManager.getInstance().applyTheme(t);
                settings.setTheme(t.getCssKey());
                SettingsManager.getInstance().save();
            });
            m.getItems().add(ri);
        }
        return m;
    }

    // ── Format Menu ───────────────────────────────────────────────────────────

    private Menu buildFormatMenu() {
        Menu m = new Menu("Format");

        Menu caseMenu = new Menu("Change Case");
        caseMenu.getItems().addAll(
            menuItem("UPPERCASE",      null, () -> activeEditor().ifPresent(e -> e.transformSelection(TextUtils::toUpperCase))),
            menuItem("lowercase",      null, () -> activeEditor().ifPresent(e -> e.transformSelection(TextUtils::toLowerCase))),
            menuItem("Title Case",     null, () -> activeEditor().ifPresent(e -> e.transformSelection(TextUtils::toTitleCase))),
            menuItem("Sentence case",  null, () -> activeEditor().ifPresent(e -> e.transformSelection(TextUtils::toSentenceCase))),
            menuItem("camelCase",      null, () -> activeEditor().ifPresent(e -> e.transformSelection(TextUtils::toCamelCase))),
            menuItem("PascalCase",     null, () -> activeEditor().ifPresent(e -> e.transformSelection(TextUtils::toPascalCase))),
            menuItem("snake_case",     null, () -> activeEditor().ifPresent(e -> e.transformSelection(TextUtils::toSnakeCase))),
            menuItem("kebab-case",     null, () -> activeEditor().ifPresent(e -> e.transformSelection(TextUtils::toKebabCase)))
        );

        Menu trimMenu = new Menu("Trim Spaces");
        trimMenu.getItems().addAll(
            menuItem("Trim Leading",   null, () -> activeEditor().ifPresent(e -> e.trimLines(true, false))),
            menuItem("Trim Trailing",  null, () -> activeEditor().ifPresent(e -> e.trimLines(false, true))),
            menuItem("Trim Both",      null, () -> activeEditor().ifPresent(e -> e.trimLines(true, true))),
            menuItem("Collapse Spaces",null, () -> activeEditor().ifPresent(
                    e -> e.transformSelection(TextUtils::collapseSpaces)))
        );

        Menu sortMenu = new Menu("Sort Lines");
        sortMenu.getItems().addAll(
            menuItem("A → Z (Case Insensitive)",  null, () -> activeEditor().ifPresent(e -> e.sortLines(true, false))),
            menuItem("Z → A (Case Insensitive)",  null, () -> activeEditor().ifPresent(e -> e.sortLines(false, false))),
            menuItem("Numeric Ascending",          null, () -> activeEditor().ifPresent(e -> e.sortLines(true, true))),
            menuItem("Numeric Descending",         null, () -> activeEditor().ifPresent(e -> e.sortLines(false, true)))
        );

        Menu encodeMenu = new Menu("Encode / Decode");
        encodeMenu.getItems().addAll(
            menuItem("Encode Base64", null, () -> activeEditor().ifPresent(e ->
                    e.transformSelection(TextUtils::encodeBase64))),
            menuItem("Decode Base64", null, () -> activeEditor().ifPresent(e ->
                    e.transformSelection(TextUtils::decodeBase64)))
        );

        m.getItems().addAll(
            caseMenu,
            trimMenu,
            sortMenu,
            new SeparatorMenuItem(),
            menuItem("Remove Empty Lines",     null, () -> activeEditor().ifPresent(EditorPane::removeEmptyLines)),
            menuItem("Remove Duplicate Lines", null, () -> activeEditor().ifPresent(EditorPane::removeDuplicateLines)),
            menuItem("Reverse Lines",          null, () -> activeEditor().ifPresent(
                    e -> e.transformSelection(TextUtils::reverseLines))),
            new SeparatorMenuItem(),
            menuItem("Join Lines",             "Ctrl+J", () -> activeEditor().ifPresent(EditorPane::joinSelectedLines)),
            menuItem("Split on Comma",         null, () -> activeEditor().ifPresent(
                    e -> e.transformSelection(t -> TextUtils.splitOnDelimiter(t, ",")))),
            menuItem("Split on Space",         null, () -> activeEditor().ifPresent(
                    e -> e.transformSelection(t -> TextUtils.splitOnDelimiter(t, " ")))),
            new SeparatorMenuItem(),
            menuItem("Reverse Text",           null, () -> activeEditor().ifPresent(
                    e -> e.transformSelection(TextUtils::reverseText))),
            menuItem("Reverse Words",          null, () -> activeEditor().ifPresent(
                    e -> e.transformSelection(TextUtils::reverseWords))),
            new SeparatorMenuItem(),
            encodeMenu,
            new SeparatorMenuItem(),
            menuItem("Text Statistics...",     null, this::showTextStats),
            buildFontMenu()
        );
        return m;
    }

    private Menu buildFontMenu() {
        Menu m = new Menu("Font");
        m.getItems().addAll(
            menuItem("Increase Font Size",  "Ctrl+]", this::fontSizeIncrease),
            menuItem("Decrease Font Size",  "Ctrl+[", this::fontSizeDecrease),
            new SeparatorMenuItem(),
            menuItem("Font Settings...", null, this::showFontSettings)
        );
        return m;
    }

    // ── Tools Menu ────────────────────────────────────────────────────────────

    private Menu buildToolsMenu() {
        Menu m = new Menu("Tools");
        m.getItems().addAll(
            menuItem("Snippets Manager...",   null,      this::showSnippets),
            menuItem("Clipboard History...",  null,      this::showClipboardHistory),
            menuItem("Bookmarks...",          "Ctrl+F2", this::toggleBookmark),
            menuItem("Next Bookmark",         "F2",      this::nextBookmark),
            menuItem("Previous Bookmark",     "Shift+F2",this::prevBookmark),
            new SeparatorMenuItem(),
            menuItem("Word Count...",         null,      this::showTextStats),
            menuItem("Read-Only Mode",        null,      this::toggleReadOnly),
            new SeparatorMenuItem(),
            menuItem("Settings...",           "Ctrl+,",  this::showSettings)
        );
        return m;
    }

    // ── Window Menu ───────────────────────────────────────────────────────────

    private Menu buildWindowMenu() {
        Menu m = new Menu("Window");
        m.getItems().addAll(
            menuItem("New Tab",             "Ctrl+T",      () -> newFile()),
            menuItem("Close Tab",           "Ctrl+W",      this::closeCurrentTab),
            menuItem("Next Tab",            "Ctrl+Tab",    this::nextTab),
            menuItem("Previous Tab",        "Ctrl+Shift+Tab", this::prevTab),
            new SeparatorMenuItem(),
            menuItem("Duplicate Tab",       null,          this::duplicateTab),
            menuItem("Pin Tab",             null,          this::pinTab)
        );
        return m;
    }

    // ── Help Menu ─────────────────────────────────────────────────────────────

    private Menu buildHelpMenu() {
        Menu m = new Menu("Help");
        m.getItems().addAll(
            menuItem("Keyboard Shortcuts", "Ctrl+Shift+/", this::showShortcuts),
            menuItem("About",              null,           this::showAbout)
        );
        return m;
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // TOOLBAR
    // ═══════════════════════════════════════════════════════════════════════════

    private ToolBar buildToolBar() {
        ToolBar tb = new ToolBar();

        btnNew    = toolBtn("📄", "New (Ctrl+N)",    () -> newFile());
        btnOpen   = toolBtn("📂", "Open (Ctrl+O)",   this::openFile);
        btnSave   = toolBtn("💾", "Save (Ctrl+S)",   this::saveFile);
        btnSaveAs = toolBtn("🖫",  "Save As",         this::saveFileAs);

        btnUndo   = toolBtn("↩", "Undo (Ctrl+Z)",    () -> activeEditor().ifPresent(EditorPane::undo));
        btnRedo   = toolBtn("↪", "Redo (Ctrl+Y)",    () -> activeEditor().ifPresent(EditorPane::redo));
        btnCut    = toolBtn("✂", "Cut (Ctrl+X)",     () -> activeEditor().ifPresent(EditorPane::cut));
        btnCopy   = toolBtn("📋", "Copy (Ctrl+C)",   () -> activeEditor().ifPresent(EditorPane::copy));
        btnPaste  = toolBtn("📌", "Paste (Ctrl+V)",  () -> activeEditor().ifPresent(EditorPane::paste));

        btnFind    = toolBtn("🔍", "Find (Ctrl+F)",   this::showFind);
        btnReplace = toolBtn("🔄", "Replace (Ctrl+H)",this::showReplace);

        Button btnPrint = toolBtn("🖨", "Print (Ctrl+P)", this::printDocument);

        // Zoom controls
        Button btnZoomOut  = toolBtn("🔍−", "Zoom Out", this::zoomOut);
        Button btnZoomIn   = toolBtn("🔍+", "Zoom In",  this::zoomIn);
        lblZoom = new Label(currentZoom + "%");
        lblZoom.getStyleClass().add("zoom-label");
        lblZoom.setPrefWidth(45);
        lblZoom.setAlignment(Pos.CENTER);

        // Theme cycle button
        btnThemeToggle = new ToggleButton("🌙");
        btnThemeToggle.getStyleClass().add("theme-toggle-btn");
        btnThemeToggle.setTooltip(new Tooltip("Toggle Theme"));
        btnThemeToggle.setOnAction(e -> ThemeManager.getInstance().cycleTheme());

        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);

        tb.getItems().addAll(
            btnNew, btnOpen, btnSave, btnSaveAs,
            new Separator(),
            btnUndo, btnRedo,
            new Separator(),
            btnCut, btnCopy, btnPaste,
            new Separator(),
            btnFind, btnReplace,
            new Separator(),
            btnPrint,
            spacer,
            btnZoomOut, lblZoom, btnZoomIn,
            new Separator(),
            btnThemeToggle
        );

        return tb;
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // SIDEBAR
    // ═══════════════════════════════════════════════════════════════════════════

    private VBox buildSidebar() {
        VBox sb = new VBox();
        sb.getStyleClass().add("sidebar");
        sb.setPrefWidth(settings.getSidebarWidth());
        sb.setMinWidth(180);

        Accordion accordion = new Accordion();
        accordion.getStyleClass().add("sidebar-accordion");
        VBox.setVgrow(accordion, Priority.ALWAYS);

        // ── Explorer ─────────────────────────────────────────────────────────
        fileExplorer = new TreeView<>(new TreeItem<>("📁 No Folder Open"));
        fileExplorer.getStyleClass().add("file-explorer");
        fileExplorer.setShowRoot(true);
        fileExplorer.setPrefHeight(200);
        fileExplorer.setOnMouseClicked(e -> {
            if (e.getClickCount() == 2) {
                TreeItem<String> sel = fileExplorer.getSelectionModel().getSelectedItem();
                if (sel != null && !sel.isLeaf()) return;
                // open file
            }
        });
        TitledPane explorerPane = new TitledPane("📁 Explorer", fileExplorer);
        explorerPane.getStyleClass().add("sidebar-pane");

        // ── Bookmarks ─────────────────────────────────────────────────────────
        bookmarkList = new ListView<>(currentBookmarks);
        bookmarkList.getStyleClass().add("bookmark-list");
        bookmarkList.setPrefHeight(150);
        bookmarkList.setCellFactory(lv -> new ListCell<>() {
            @Override
            protected void updateItem(BookmarkModel bm, boolean empty) {
                super.updateItem(bm, empty);
                setText(empty || bm == null ? null : "🔖 " + bm.toString());
            }
        });
        bookmarkList.setOnMouseClicked(e -> {
            if (e.getClickCount() == 2) {
                BookmarkModel bm = bookmarkList.getSelectionModel().getSelectedItem();
                if (bm != null) activeEditor().ifPresent(ed -> ed.scrollToLine(bm.getLineNumber()));
            }
        });

        Button btnAddBm = smallBtn("+ Add",    this::toggleBookmark);
        Button btnDelBm = smallBtn("✕ Remove", () -> {
            BookmarkModel sel = bookmarkList.getSelectionModel().getSelectedItem();
            if (sel != null) {
                currentBookmarks.remove(sel);
                if (sel.getFilePath() != null)
                    DatabaseManager.getInstance().removeBookmark(sel.getFilePath(), sel.getLineNumber());
            }
        });
        HBox bmBtns = new HBox(5, btnAddBm, btnDelBm);
        bmBtns.setPadding(new Insets(4));

        VBox bmBox = new VBox(bookmarkList, bmBtns);
        TitledPane bmPane = new TitledPane("🔖 Bookmarks", bmBox);
        bmPane.getStyleClass().add("sidebar-pane");

        // ── Recent Files ──────────────────────────────────────────────────────
        recentFilesList = new ListView<>(RecentFilesManager.getInstance().getRecentFiles());
        recentFilesList.getStyleClass().add("recent-list");
        recentFilesList.setPrefHeight(180);
        recentFilesList.setCellFactory(lv -> new ListCell<>() {
            @Override
            protected void updateItem(String path, boolean empty) {
                super.updateItem(path, empty);
                if (empty || path == null) { setText(null); setTooltip(null); }
                else {
                    setText("📄 " + new File(path).getName());
                    setTooltip(new Tooltip(path));
                }
            }
        });
        recentFilesList.setOnMouseClicked(e -> {
            if (e.getClickCount() == 2) {
                String path = recentFilesList.getSelectionModel().getSelectedItem();
                if (path != null) openFileByPath(path);
            }
        });

        Button btnClearRecent = smallBtn("Clear", () -> RecentFilesManager.getInstance().clear());
        VBox recentBox = new VBox(recentFilesList, new HBox(4, btnClearRecent));
        ((HBox) recentBox.getChildren().get(1)).setPadding(new Insets(4));
        TitledPane recentPane = new TitledPane("🕐 Recent Files", recentBox);
        recentPane.getStyleClass().add("sidebar-pane");

        // ── Snippets ──────────────────────────────────────────────────────────
        snippetList = new ListView<>();
        snippetList.getStyleClass().add("snippet-list");
        snippetList.setPrefHeight(180);
        snippetList.getItems().setAll(DatabaseManager.getInstance().getSnippets());
        snippetList.setCellFactory(lv -> new ListCell<>() {
            @Override
            protected void updateItem(SnippetModel s, boolean empty) {
                super.updateItem(s, empty);
                setText(empty || s == null ? null : "⚡ " + s.getName());
                if (s != null) setTooltip(new Tooltip(s.getCategory()));
            }
        });
        snippetList.setOnMouseClicked(e -> {
            if (e.getClickCount() == 2) {
                SnippetModel sel = snippetList.getSelectionModel().getSelectedItem();
                if (sel != null) insertText(sel.getContent());
            }
        });

        Button btnManageSnippets = smallBtn("Manage...", this::showSnippets);
        VBox snippetBox = new VBox(snippetList, new HBox(4, btnManageSnippets));
        ((HBox) snippetBox.getChildren().get(1)).setPadding(new Insets(4));
        TitledPane snippetPane = new TitledPane("⚡ Snippets", snippetBox);
        snippetPane.getStyleClass().add("sidebar-pane");

        // ── Notes ─────────────────────────────────────────────────────────────
        notesArea = new TextArea();
        notesArea.getStyleClass().add("notes-area");
        notesArea.setPromptText("Quick notes (not saved to file)...");
        notesArea.setPrefHeight(150);
        notesArea.setWrapText(true);
        TitledPane notesPane = new TitledPane("📝 Quick Notes", notesArea);
        notesPane.getStyleClass().add("sidebar-pane");

        accordion.getPanes().addAll(explorerPane, bmPane, recentPane, snippetPane, notesPane);
        accordion.setExpandedPane(explorerPane);

        sb.getChildren().add(accordion);
        return sb;
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // EDITOR AREA
    // ═══════════════════════════════════════════════════════════════════════════

    private BorderPane buildEditorArea() {
        BorderPane bp = new BorderPane();
        bp.getStyleClass().add("editor-area");

        // Tab pane
        tabPane = new TabPane();
        tabPane.getStyleClass().add("editor-tab-pane");
        tabPane.setTabClosingPolicy(TabPane.TabClosingPolicy.ALL_TABS);
        tabPane.getSelectionModel().selectedItemProperty().addListener(
                (obs, old, newTab) -> onTabChanged(newTab));

        bp.setCenter(tabPane);

        // Find bar (hidden)
        findBar = new FindReplaceBar(this);
        findBar.setVisible(false);
        findBar.setManaged(false);
        bp.setTop(findBar);

        return bp;
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // STATUS BAR
    // ═══════════════════════════════════════════════════════════════════════════

    private HBox buildStatusBar() {
        HBox bar = new HBox(12);
        bar.getStyleClass().add("status-bar");
        bar.setAlignment(Pos.CENTER_LEFT);
        bar.setPadding(new Insets(2, 12, 2, 12));

        lblLine       = statusLabel("Ln 1");
        lblCol        = statusLabel("Col 1");
        lblWords      = statusLabel("0 words");
        lblChars      = statusLabel("0 chars");
        lblEncoding   = statusLabel("UTF-8");
        lblLang       = statusLabel("Plain Text");
        lblZoomStatus = statusLabel(currentZoom + "%");
        lblModified   = statusLabel("");
        lblAutoSave   = statusLabel("");

        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);

        bar.getChildren().addAll(
            lblLine, sep(), lblCol, sep(),
            lblWords, sep(), lblChars, sep(),
            spacer,
            lblModified, sep(),
            lblAutoSave, sep(),
            lblEncoding, sep(),
            lblLang, sep(),
            lblZoomStatus
        );
        return bar;
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // STARTUP / SESSION
    // ═══════════════════════════════════════════════════════════════════════════

    public void startup() {
        List<SessionEntry> session = SessionManager.getInstance().loadSession();

        if (session.isEmpty()) {
            newFile();
        } else {
            for (SessionEntry e : session) {
                if (e.filePath != null && !e.filePath.isEmpty()) {
                    openFileByPath(e.filePath);
                } else {
                    // Restored unsaved document
                    DocumentModel doc = new DocumentModel();
                    doc.setTitle(e.title != null ? e.title : "Untitled");
                    EditorPane ep = createEditorPane(doc);
                    Tab tab = createTab(doc, ep);
                    tabPane.getTabs().add(tab);
                    ep.setContent(e.content != null ? e.content : "");
                }
            }
            if (!tabPane.getTabs().isEmpty())
                tabPane.getSelectionModel().selectFirst();
        }

        // Auto-save
        AutoSaveManager.getInstance().start(this::autoSaveAll);

        // Backup scheduler
        BackupManager.getInstance().startScheduler(this::backupAll);

        // Status bar refresh
        startStatusBarTimer();
    }

    private void startStatusBarTimer() {
        javafx.animation.Timeline tl = new javafx.animation.Timeline(
            new javafx.animation.KeyFrame(
                javafx.util.Duration.millis(500),
                e -> updateStatusBar()
            )
        );
        tl.setCycleCount(javafx.animation.Animation.INDEFINITE);
        tl.play();
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // TAB MANAGEMENT
    // ═══════════════════════════════════════════════════════════════════════════

    private void newFile() {
        DocumentModel doc = new DocumentModel();
        doc.setTitle("Untitled " + untitledCounter++);

        EditorPane ep  = createEditorPane(doc);
        Tab tab        = createTab(doc, ep);
        tabPane.getTabs().add(tab);
        tabPane.getSelectionModel().select(tab);
        ep.focusEditor();
    }

    private EditorPane createEditorPane(DocumentModel doc) {
        EditorPane ep = new EditorPane(doc);
        ep.setOnModified(d -> {
            // Update tab title
            Tab t = findTabForDoc(d);
            if (t != null) t.setText(d.getDisplayTitle());
            updateStatusBar();
        });
        ep.setOnCustomZoom(zoomDir -> {
            if (zoomDir > 0) zoomIn();
            else zoomOut();
        });
        ep.setZoom(currentZoom);
        return ep;
    }

    private Tab createTab(DocumentModel doc, EditorPane ep) {
        Tab tab = new Tab(doc.getDisplayTitle(), ep);
        tab.getStyleClass().add("editor-tab");
        tab.setTooltip(new Tooltip(doc.getFile() != null ? doc.getFile().getAbsolutePath() : "New file"));

        tab.setOnCloseRequest(e -> {
            if (doc.isModified()) {
                ButtonType save   = new ButtonType("Save");
                ButtonType noSave = new ButtonType("Don't Save");
                ButtonType cancel = new ButtonType("Cancel", ButtonBar.ButtonData.CANCEL_CLOSE);
                Alert a = alert(AlertType.CONFIRMATION,
                        "Unsaved Changes",
                        "Save changes to \"" + doc.getTitle() + "\" before closing?");
                a.getButtonTypes().setAll(save, noSave, cancel);
                Optional<ButtonType> result = a.showAndWait();
                if (result.isEmpty() || result.get() == cancel) {
                    e.consume();
                    return;
                }
                if (result.get() == save) {
                    saveDocumentSync(ep);
                }
            }
            ep.dispose();
        });

        tabs.add(new TabEntry(tab, ep, doc));
        return tab;
    }

    private void onTabChanged(Tab newTab) {
        if (newTab == null) return;
        getEditorFor(newTab).ifPresent(ep -> {
            updateStatusBar();
            refreshBookmarks(ep.getDocument());
            ep.focusEditor();
        });
    }

    private Tab findTabForDoc(DocumentModel doc) {
        for (TabEntry e : tabs) {
            if (e.doc == doc) return e.tab;
        }
        return null;
    }

    /** Public accessor used by FindReplaceBar. */
    public Optional<EditorPane> activeEditor_public() { return activeEditor(); }

    private Optional<EditorPane> activeEditor() {
        Tab sel = tabPane.getSelectionModel().getSelectedItem();
        return getEditorFor(sel);
    }

    private Optional<EditorPane> getEditorFor(Tab tab) {
        if (tab == null) return Optional.empty();
        for (TabEntry e : tabs) {
            if (e.tab == tab) return Optional.of(e.editor);
        }
        return Optional.empty();
    }

    private void closeCurrentTab() {
        Tab sel = tabPane.getSelectionModel().getSelectedItem();
        if (sel != null) {
            tabPane.getTabs().remove(sel);
            tabs.removeIf(e -> e.tab == sel);
        }
    }

    private void closeAllTabs() {
        tabPane.getTabs().clear();
        tabs.clear();
    }

    private void nextTab() {
        int idx = tabPane.getSelectionModel().getSelectedIndex();
        tabPane.getSelectionModel().select((idx + 1) % tabPane.getTabs().size());
    }

    private void prevTab() {
        int idx = tabPane.getSelectionModel().getSelectedIndex();
        tabPane.getSelectionModel().select(idx == 0 ? tabPane.getTabs().size() - 1 : idx - 1);
    }

    private void duplicateTab() {
        activeEditor().ifPresent(ep -> {
            DocumentModel orig = ep.getDocument();
            DocumentModel dup  = new DocumentModel(orig.getFile());
            dup.setTitle(orig.getTitle() + " (copy)");
            EditorPane newEp   = createEditorPane(dup);
            Tab tab            = createTab(dup, newEp);
            tabPane.getTabs().add(tab);
            tabPane.getSelectionModel().select(tab);
            newEp.setContent(ep.getAllText());
        });
    }

    private void pinTab() {
        Tab sel = tabPane.getSelectionModel().getSelectedItem();
        if (sel != null) {
            boolean pinned = Boolean.TRUE.equals(sel.getUserData());
            sel.setUserData(!pinned);
            sel.setClosable(pinned); // If pinned → not closable
        }
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // FILE OPERATIONS
    // ═══════════════════════════════════════════════════════════════════════════

    private void openFile() {
        FileChooser fc = fileChooser("Open File");
        File file = fc.showOpenDialog(stage);
        if (file != null) openFileByPath(file.getAbsolutePath());
    }

    public void openFileByPath(String path) {
        File file = new File(path);
        if (!file.exists()) {
            showError("File Not Found", "The file no longer exists:\n" + path);
            return;
        }

        // Check if already open
        for (TabEntry e : tabs) {
            if (e.doc.getFile() != null && e.doc.getFile().getAbsolutePath().equals(file.getAbsolutePath())) {
                tabPane.getSelectionModel().select(e.tab);
                return;
            }
        }

        DocumentModel doc = new DocumentModel(file);
        EditorPane ep     = createEditorPane(doc);
        Tab tab           = createTab(doc, ep);
        tabPane.getTabs().add(tab);
        tabPane.getSelectionModel().select(tab);

        // Async read
        FileManager.getInstance().readAsync(file, settings.getDefaultEncoding(),
            content -> {
                ep.setContent(content);
                ep.focusEditor();
                RecentFilesManager.getInstance().add(file.getAbsolutePath());
                refreshFileExplorer(file.getParentFile());
            },
            ex -> showError("Open Failed", "Could not open file:\n" + ex.getMessage())
        );
    }

    private void saveFile() {
        activeEditor().ifPresent(this::saveDocumentAsync);
    }

    private void saveFileAs() {
        activeEditor().ifPresent(ep -> {
            FileChooser fc = fileChooser("Save As");
            fc.setInitialFileName(ep.getDocument().getTitle());
            File file = fc.showSaveDialog(stage);
            if (file != null) {
                ep.getDocument().setFile(file);
                saveDocumentAsync(ep);
            }
        });
    }

    private void saveAllFiles() {
        for (TabEntry e : tabs) {
            if (e.doc.isModified()) saveDocumentAsync(e.editor);
        }
    }

    private void saveDocumentAsync(EditorPane ep) {
        DocumentModel doc = ep.getDocument();
        if (!ensureFileAssigned(doc)) return;
        String content = ep.getAllText();
        FileManager.getInstance().writeAsync(doc.getFile(), content, settings.getDefaultEncoding(),
            () -> {
                doc.setModified(false);
                doc.setLastSavedAt(System.currentTimeMillis());
                Tab t = findTabForDoc(doc);
                if (t != null) t.setText(doc.getDisplayTitle());
                lblAutoSave.setText("✔ Saved");
                Platform.runLater(() -> {
                    javafx.animation.PauseTransition pt = new javafx.animation.PauseTransition(
                            javafx.util.Duration.seconds(3));
                    pt.setOnFinished(e -> lblAutoSave.setText(""));
                    pt.play();
                });
                BackupManager.getInstance().backup(doc.getFile(), content);
            },
            ex -> showError("Save Failed", ex.getMessage())
        );
    }

    private void saveDocumentSync(EditorPane ep) {
        DocumentModel doc = ep.getDocument();
        if (!ensureFileAssigned(doc)) return;
        try {
            FileManager.getInstance().writeSync(doc.getFile(), ep.getAllText(), settings.getDefaultEncoding());
            doc.setModified(false);
        } catch (Exception e) {
            showError("Save Failed", e.getMessage());
        }
    }

    private boolean ensureFileAssigned(DocumentModel doc) {
        if (doc.getFile() != null) return true;
        try {
            File defaultDir = new File(System.getProperty("user.home") + "/.notepact/notes");
            if (!defaultDir.exists()) {
                defaultDir.mkdirs();
            }
            
            String safeTitle = doc.getTitle().replaceAll("[\\\\/:*?\"<>|]", "_");
            File file = new File(defaultDir, safeTitle + ".txt");
            int counter = 1;
            while (file.exists()) {
                file = new File(defaultDir, safeTitle + "_" + counter + ".txt");
                counter++;
            }
            
            doc.setFile(file);
            doc.setTitle(file.getName());
            
            Tab t = findTabForDoc(doc);
            if (t != null) {
                t.setText(doc.getDisplayTitle());
                t.setTooltip(new Tooltip(file.getAbsolutePath()));
            }
            return true;
        } catch (Exception e) {
            showError("File Creation Failed", "Could not create default file:\n" + e.getMessage());
            return false;
        }
    }



    private void autoSaveAll() {
        for (TabEntry e : tabs) {
            if (e.doc.isModified()) {
                saveDocumentAsync(e.editor);
            }
        }
    }

    private void backupAll() {
        for (TabEntry e : tabs) {
            if (e.doc.getFile() != null) {
                BackupManager.getInstance().backup(e.doc.getFile(), e.editor.getAllText());
            }
        }
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // FIND / REPLACE
    // ═══════════════════════════════════════════════════════════════════════════

    public void showFind() {
        findBar.setReplaceMode(false);
        toggleFindBar(true);
        findBar.focusSearch();
    }

    public void showReplace() {
        findBar.setReplaceMode(true);
        toggleFindBar(true);
        findBar.focusSearch();
    }

    private void toggleFindBar(boolean show) {
        findBarVisible = show;
        findBar.setVisible(show);
        findBar.setManaged(show);
        if (!show) {
            activeEditor().ifPresent(EditorPane::clearSearchHighlights);
        }
    }

    public void closeFindBar() { toggleFindBar(false); }

    public void findNext() { findBar.findNext(); }
    public void findPrevious() { findBar.findPrevious(); }

    // ═══════════════════════════════════════════════════════════════════════════
    // BOOKMARKS
    // ═══════════════════════════════════════════════════════════════════════════

    private void toggleBookmark() {
        activeEditor().ifPresent(ep -> {
            DocumentModel doc = ep.getDocument();
            int line   = ep.getCurrentLine();
            String fp  = doc.getFile() != null ? doc.getFile().getAbsolutePath() : doc.getId();

            boolean exists = DatabaseManager.getInstance().isBookmarked(fp, line);
            if (exists) {
                DatabaseManager.getInstance().removeBookmark(fp, line);
            } else {
                BookmarkModel bm = new BookmarkModel(fp, line, "");
                DatabaseManager.getInstance().addBookmark(bm);
            }
            refreshBookmarks(doc);
        });
    }

    private void refreshBookmarks(DocumentModel doc) {
        if (doc == null) return;
        String fp = doc.getFile() != null ? doc.getFile().getAbsolutePath() : doc.getId();
        List<BookmarkModel> bms = DatabaseManager.getInstance().getBookmarks(fp);
        Platform.runLater(() -> currentBookmarks.setAll(bms));
    }

    private void nextBookmark() {
        activeEditor().ifPresent(ep -> {
            if (currentBookmarks.isEmpty()) return;
            int cur = ep.getCurrentLine();
            BookmarkModel next = currentBookmarks.stream()
                    .filter(bm -> bm.getLineNumber() > cur)
                    .findFirst()
                    .orElse(currentBookmarks.get(0));
            ep.scrollToLine(next.getLineNumber());
        });
    }

    private void prevBookmark() {
        activeEditor().ifPresent(ep -> {
            if (currentBookmarks.isEmpty()) return;
            int cur = ep.getCurrentLine();
            BookmarkModel prev = null;
            for (BookmarkModel bm : currentBookmarks) {
                if (bm.getLineNumber() < cur) prev = bm;
            }
            if (prev == null) prev = currentBookmarks.get(currentBookmarks.size() - 1);
            ep.scrollToLine(prev.getLineNumber());
        });
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // EXPORT / PRINT
    // ═══════════════════════════════════════════════════════════════════════════

    private void exportAs(String format) {
        activeEditor().ifPresent(ep -> {
            FileChooser fc = new FileChooser();
            fc.setTitle("Export As " + format.toUpperCase());
            fc.getExtensionFilters().add(new FileChooser.ExtensionFilter(
                    format.toUpperCase() + " files", "*." + format));
            File dest = fc.showSaveDialog(stage);
            if (dest == null) return;

            try {
                String content = ep.getAllText();
                DocumentModel doc = ep.getDocument();
                switch (format) {
                    case "txt"  -> ExportManager.exportTxt(dest, content);
                    case "html" -> ExportManager.exportHtml(dest, content, doc);
                    case "md"   -> ExportManager.exportMarkdown(dest, content);
                    case "pdf"  -> ExportManager.exportPdf(dest, content, doc);
                }
                showInfo("Export Successful", "File exported to:\n" + dest.getAbsolutePath());
            } catch (Exception e) {
                showError("Export Failed", e.getMessage());
            }
        });
    }

    private void printDocument() {
        activeEditor().ifPresent(ep -> {
            javafx.print.PrinterJob job = javafx.print.PrinterJob.createPrinterJob();
            if (job != null && job.showPrintDialog(stage)) {
                boolean printed = job.printPage(ep.getCodeArea());
                if (printed) job.endJob();
            }
        });
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // STATUS BAR
    // ═══════════════════════════════════════════════════════════════════════════

    private void updateStatusBar() {
        activeEditor().ifPresent(ep -> {
            DocumentModel doc = ep.getDocument();
            int sel = ep.getSelectionLength();
            lblLine.setText("Ln " + ep.getCurrentLine());
            lblCol.setText("Col " + ep.getCurrentColumn());
            lblWords.setText(ep.getWordCount() + " words");
            lblChars.setText((sel > 0 ? sel + "/" : "") + ep.getCharCount() + " chars");
            lblEncoding.setText(doc.getEncoding());
            lblLang.setText(doc.getLanguage());
            lblZoomStatus.setText(currentZoom + "%");
            lblModified.setText(doc.isModified() ? "● Modified" : "");
        });
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // ZOOM
    // ═══════════════════════════════════════════════════════════════════════════

    private void zoomIn()    { setZoom(Math.min(currentZoom + 10, 300)); }
    private void zoomOut()   { setZoom(Math.max(currentZoom - 10, 50));  }
    private void zoomReset() { setZoom(100); }

    private void setZoom(int z) {
        currentZoom = z;
        settings.setZoomLevel(z);
        SettingsManager.getInstance().save();
        if (lblZoom != null) lblZoom.setText(z + "%");
        if (lblZoomStatus != null) lblZoomStatus.setText(z + "%");
        for (TabEntry e : tabs) e.editor.setZoom(z);
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // FONT
    // ═══════════════════════════════════════════════════════════════════════════

    private void fontSizeIncrease() { settings.setFontSize(settings.getFontSize() + 1); applyFontToAll(); }
    private void fontSizeDecrease() { settings.setFontSize(Math.max(6, settings.getFontSize() - 1)); applyFontToAll(); }

    private void applyFontToAll() {
        SettingsManager.getInstance().save();
        for (TabEntry e : tabs) e.editor.applySettings();
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // DIALOGS
    // ═══════════════════════════════════════════════════════════════════════════

    private void showSettings() {
        new SettingsDialog(stage, settings, this::applyFontToAll).show();
    }

    private void showFontSettings() { showSettings(); }

    private void showTextStats() {
        activeEditor().ifPresent(ep -> {
            String text = ep.getAllText();
            String info = """
                    Characters:              %,d
                    Characters (no spaces):  %,d
                    Words:                   %,d
                    Lines:                   %,d
                    Paragraphs:              %,d
                    Reading Time:            %s
                    """.formatted(
                    TextUtils.countChars(text),
                    TextUtils.countCharsNoSpaces(text),
                    TextUtils.countWords(text),
                    TextUtils.countLines(text),
                    TextUtils.countParagraphs(text),
                    TextUtils.readingTimeFormatted(text));
            showInfo("Text Statistics — " + ep.getDocument().getTitle(), info);
        });
    }

    private void showShortcuts() {
        String shortcuts = """
                ── File ───────────────────────────────────
                Ctrl+N           New File
                Ctrl+O           Open File
                Ctrl+S           Save
                Ctrl+Shift+S     Save As
                Ctrl+W           Close Tab
                Ctrl+P           Print
                
                ── Edit ───────────────────────────────────
                Ctrl+Z           Undo
                Ctrl+Y           Redo
                Ctrl+X           Cut
                Ctrl+C           Copy
                Ctrl+V           Paste
                Ctrl+A           Select All
                Ctrl+F           Find
                Ctrl+H           Replace
                F3               Find Next
                Shift+F3         Find Previous
                F5               Insert Date/Time
                
                ── Line Operations ────────────────────────
                Ctrl+D           Duplicate Line
                Ctrl+L           Delete Line
                Ctrl+J           Join Lines
                Alt+Up           Move Line Up
                Alt+Down         Move Line Down
                Shift+Alt+Up     Copy Line Up
                Shift+Alt+Down   Copy Line Down
                
                ── Bookmarks ──────────────────────────────
                Ctrl+F2          Toggle Bookmark
                F2               Next Bookmark
                Shift+F2         Previous Bookmark
                
                ── View ───────────────────────────────────
                Ctrl++           Zoom In
                Ctrl+-           Zoom Out
                Ctrl+0           Reset Zoom
                Ctrl+,           Settings
                Ctrl+Tab         Next Tab
                Ctrl+Shift+Tab   Previous Tab
                """;

        Alert a = new Alert(AlertType.INFORMATION);
        a.setTitle("Keyboard Shortcuts");
        a.setHeaderText("Notepact — Keyboard Shortcuts");
        TextArea ta = new TextArea(shortcuts);
        ta.setEditable(false);
        ta.setFont(Font.font("Consolas", 12));
        ta.setPrefSize(480, 500);
        a.getDialogPane().setContent(ta);
        ThemeManager.getInstance().getScene().getStylesheets()
                    .forEach(s -> a.getDialogPane().getScene().getStylesheets().add(s));
        a.showAndWait();
    }

    private void showAbout() {
        showInfo("About Notepact",
                "Notepact v1.0.0\n\n" +
                "A modern, professional desktop text editor.\n" +
                "Built with Java 21 + JavaFX 21 + RichTextFX.\n\n" +
                "Features: Multi-tab editing, Syntax highlighting,\n" +
                "Auto-save, Backup system, Find/Replace with Regex,\n" +
                "5 themes, PDF export, and much more.\n\n" +
                "© 2024 Notepact Project");
    }

    private void showSnippets() {
        // Simple snippet manager dialog
        Dialog<ButtonType> dlg = new Dialog<>();
        dlg.setTitle("Snippets Manager");
        dlg.setHeaderText("Manage reusable text snippets");
        dlg.getDialogPane().getButtonTypes().addAll(ButtonType.OK, ButtonType.CANCEL);

        ListView<SnippetModel> lv = new ListView<>();
        lv.getItems().setAll(DatabaseManager.getInstance().getSnippets());
        lv.setPrefSize(400, 300);
        lv.setCellFactory(x -> new ListCell<>() {
            @Override
            protected void updateItem(SnippetModel s, boolean empty) {
                super.updateItem(s, empty);
                setText(empty ? null : "[" + s.getCategory() + "] " + s.getName());
            }
        });

        Button btnInsert = new Button("Insert into Editor");
        btnInsert.setOnAction(e -> {
            SnippetModel sel = lv.getSelectionModel().getSelectedItem();
            if (sel != null) { insertText(sel.getContent()); dlg.close(); }
        });

        Button btnDelete = new Button("Delete");
        btnDelete.setOnAction(e -> {
            SnippetModel sel = lv.getSelectionModel().getSelectedItem();
            if (sel != null) {
                DatabaseManager.getInstance().deleteSnippet(sel.getId());
                lv.getItems().remove(sel);
                snippetList.getItems().setAll(DatabaseManager.getInstance().getSnippets());
            }
        });

        HBox btns = new HBox(8, btnInsert, btnDelete);
        btns.setPadding(new Insets(8, 0, 0, 0));
        VBox content = new VBox(8, lv, btns);
        dlg.getDialogPane().setContent(content);
        applyDialogTheme(dlg.getDialogPane());
        dlg.showAndWait();
    }

    private void showClipboardHistory() {
        List<String> history = DatabaseManager.getInstance().getClipboardHistory();
        Dialog<ButtonType> dlg = new Dialog<>();
        dlg.setTitle("Clipboard History");
        dlg.setHeaderText("Last 100 clipboard entries");
        dlg.getDialogPane().getButtonTypes().addAll(ButtonType.CLOSE);

        ListView<String> lv = new ListView<>();
        lv.getItems().setAll(history);
        lv.setPrefSize(460, 350);
        lv.setCellFactory(x -> new ListCell<>() {
            @Override
            protected void updateItem(String s, boolean empty) {
                super.updateItem(s, empty);
                if (empty || s == null) { setText(null); return; }
                String preview = s.replace("\n", "↵").replace("\t", "→");
                setText(preview.length() > 80 ? preview.substring(0, 77) + "…" : preview);
                setTooltip(new Tooltip(s.length() > 200 ? s.substring(0, 200) + "…" : s));
            }
        });

        Button btnInsert = new Button("Insert");
        btnInsert.setOnAction(e -> {
            String sel = lv.getSelectionModel().getSelectedItem();
            if (sel != null) insertText(sel);
        });

        Button btnClear = new Button("Clear History");
        btnClear.setOnAction(e -> {
            DatabaseManager.getInstance().clearClipboardHistory();
            lv.getItems().clear();
        });

        HBox btns = new HBox(8, btnInsert, btnClear);
        btns.setPadding(new Insets(8, 0, 0, 0));
        VBox content = new VBox(8, lv, btns);
        dlg.getDialogPane().setContent(content);
        applyDialogTheme(dlg.getDialogPane());
        dlg.showAndWait();
    }

    private void toggleReadOnly() {
        activeEditor().ifPresent(ep -> {
            DocumentModel doc = ep.getDocument();
            boolean ro = !doc.isReadOnly();
            doc.setReadOnly(ro);
            ep.getCodeArea().setEditable(!ro);
            showInfo("Read-Only Mode", ro ? "Read-only mode ENABLED" : "Read-only mode DISABLED");
        });
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // GLOBAL KEYBOARD SHORTCUTS
    // ═══════════════════════════════════════════════════════════════════════════

    private void handleGlobalKeys(KeyEvent e) {
        boolean ctrl  = e.isControlDown();
        boolean shift = e.isShiftDown();
        boolean alt   = e.isAltDown();
        KeyCode k     = e.getCode();

        if (ctrl && !alt) {
            if (k == KeyCode.EQUALS || k == KeyCode.PLUS || k == KeyCode.ADD) {
                zoomIn();
                e.consume();
                return;
            }
            if (k == KeyCode.MINUS || k == KeyCode.SUBTRACT) {
                zoomOut();
                e.consume();
                return;
            }
            if (k == KeyCode.DIGIT0 || k == KeyCode.NUMPAD0) {
                zoomReset();
                e.consume();
                return;
            }
        }

        if (ctrl && !shift && !alt) {
            switch (k) {
                case N  -> { newFile();         e.consume(); }
                case O  -> { openFile();        e.consume(); }
                case S  -> { saveFile();        e.consume(); }
                case W  -> { closeCurrentTab(); e.consume(); }
                case F  -> { showFind();        e.consume(); }
                case H  -> { showReplace();     e.consume(); }
                case Z  -> { activeEditor().ifPresent(EditorPane::undo); e.consume(); }
                case Y  -> { activeEditor().ifPresent(EditorPane::redo); e.consume(); }
                case TAB-> { nextTab();         e.consume(); }
                case T  -> { newFile();         e.consume(); }
                case P  -> { printDocument();   e.consume(); }
                case COMMA -> { showSettings(); e.consume(); }
                case CLOSE_BRACKET -> { fontSizeIncrease(); e.consume(); }
                case OPEN_BRACKET  -> { fontSizeDecrease(); e.consume(); }
                default -> {}
            }
        }

        if (ctrl && shift && !alt) {
            switch (k) {
                case S   -> { saveFileAs();     e.consume(); }
                case N   -> { newWindow();      e.consume(); }
                case W   -> { closeAllTabs();   e.consume(); }
                case TAB -> { prevTab();        e.consume(); }
                case SLASH -> { showShortcuts();e.consume(); }
                default -> {}
            }
        }

        if (!ctrl && !shift && !alt) {
            switch (k) {
                case F2 -> { nextBookmark(); e.consume(); }
                case F5 -> { insertText(TextUtils.getDateTime_Full()); e.consume(); }
                case ESCAPE -> { closeFindBar(); e.consume(); }
                default -> {}
            }
        }

        if (!ctrl && shift && !alt && k == KeyCode.F2) {
            prevBookmark(); e.consume();
        }

        if (ctrl && !shift && !alt && k == KeyCode.F2) {
            toggleBookmark(); e.consume();
        }
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // HELPER METHODS
    // ═══════════════════════════════════════════════════════════════════════════

    public void insertText(String text) {
        activeEditor().ifPresent(ep -> ep.getCodeArea().insertText(
                ep.getCodeArea().getCaretPosition(), text));
    }

    private void refreshFileExplorer(File dir) {
        if (dir == null || !dir.isDirectory()) return;
        TreeItem<String> root = new TreeItem<>("📁 " + dir.getName());
        File[] files = dir.listFiles();
        if (files != null) {
            Arrays.sort(files, (a, b) -> {
                int dirComp = Boolean.compare(!a.isDirectory(), !b.isDirectory());
                return dirComp != 0 ? dirComp : a.getName().compareToIgnoreCase(b.getName());
            });
            for (File f : files) {
                TreeItem<String> item = new TreeItem<>((f.isDirectory() ? "📁 " : "📄 ") + f.getName());
                root.getChildren().add(item);
            }
        }
        root.setExpanded(true);
        Platform.runLater(() -> fileExplorer.setRoot(root));
    }

    private void populateRecentMenu(Menu menu) {
        menu.getItems().clear();
        List<String> recent = DatabaseManager.getInstance().getRecentFiles();
        if (recent.isEmpty()) {
            menu.getItems().add(new MenuItem("(No recent files)"));
            return;
        }
        for (String path : recent) {
            MenuItem mi = new MenuItem(new File(path).getName());
            mi.setOnAction(e -> openFileByPath(path));
            mi.setStyle("-fx-font-size: 12px;");
            menu.getItems().add(mi);
        }
    }

    private void newWindow() {
        Stage newStage = new Stage();
        MainController ctrl = new MainController(newStage);
        Scene scene = ctrl.buildScene();
        newStage.setScene(scene);
        newStage.setTitle("Notepact");
        newStage.show();
        ctrl.startup();
    }

    // ── Window close ─────────────────────────────────────────────────────────

    public void onWindowClose() {
        // Save session
        List<SessionEntry> entries = new ArrayList<>();
        int idx = 0;
        for (TabEntry te : tabs) {
            SessionEntry se = new SessionEntry();
            se.tabIndex = idx++;
            se.filePath = te.doc.getFile() != null ? te.doc.getFile().getAbsolutePath() : null;
            se.title    = te.doc.getTitle();
            se.content  = te.doc.isModified() ? te.editor.getAllText() : "";
            se.caretPos = te.editor.getCodeArea().getCaretPosition();
            entries.add(se);
        }
        SessionManager.getInstance().saveSession(entries);

        // Save window state
        settings.setWindowWidth(stage.getWidth());
        settings.setWindowHeight(stage.getHeight());
        settings.setWindowX(stage.getX());
        settings.setWindowY(stage.getY());
        settings.setMaximized(stage.isMaximized());
        SettingsManager.getInstance().save();

        // Shutdown background threads
        AutoSaveManager.getInstance().shutdown();
        BackupManager.getInstance().shutdown();
        FileManager.getInstance().shutdown();
        DatabaseManager.getInstance().close();
    }

    private void exitApp() {
        stage.fireEvent(new WindowEvent(stage, WindowEvent.WINDOW_CLOSE_REQUEST));
    }

    // ── UI helpers ────────────────────────────────────────────────────────────

    private MenuItem menuItem(String text, String shortcut, Runnable action) {
        MenuItem mi = new MenuItem(text);
        if (shortcut != null) mi.setAccelerator(KeyCombination.keyCombination(shortcut));
        mi.setOnAction(e -> action.run());
        return mi;
    }

    private Button toolBtn(String icon, String tooltip, Runnable action) {
        Button b = new Button(icon);
        b.getStyleClass().add("tool-button");
        b.setTooltip(new Tooltip(tooltip));
        b.setOnAction(e -> action.run());
        return b;
    }

    private Button smallBtn(String text, Runnable action) {
        Button b = new Button(text);
        b.getStyleClass().add("small-btn");
        b.setOnAction(e -> action.run());
        b.setMaxWidth(Double.MAX_VALUE);
        HBox.setHgrow(b, Priority.ALWAYS);
        return b;
    }

    private Label statusLabel(String text) {
        Label l = new Label(text);
        l.getStyleClass().add("status-label");
        return l;
    }

    private Label sep() {
        Label l = new Label("|");
        l.getStyleClass().add("status-sep");
        return l;
    }

    private FileChooser fileChooser(String title) {
        FileChooser fc = new FileChooser();
        fc.setTitle(title);
        fc.getExtensionFilters().addAll(
            new FileChooser.ExtensionFilter(FileManager.FILTER_DESCRIPTION, FileManager.SUPPORTED_EXTENSIONS),
            new FileChooser.ExtensionFilter("All Files", "*.*")
        );
        return fc;
    }

    private Alert alert(AlertType type, String title, String content) {
        Alert a = new Alert(type);
        a.setTitle(title);
        a.setHeaderText(null);
        a.setContentText(content);
        applyDialogTheme(a.getDialogPane());
        return a;
    }

    private void showError(String title, String msg) {
        Platform.runLater(() -> alert(AlertType.ERROR, title, msg).showAndWait());
    }

    private void showInfo(String title, String msg) {
        Alert a = new Alert(AlertType.INFORMATION);
        a.setTitle(title);
        a.setHeaderText(null);
        TextArea ta = new TextArea(msg);
        ta.setEditable(false);
        ta.setWrapText(true);
        ta.setPrefSize(420, 200);
        a.getDialogPane().setContent(ta);
        applyDialogTheme(a.getDialogPane());
        a.showAndWait();
    }

    private void applyDialogTheme(DialogPane dp) {
        Scene s = ThemeManager.getInstance().getScene();
        if (s != null) {
            dp.getScene().getStylesheets().addAll(s.getStylesheets());
        }
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // INNER CLASSES
    // ═══════════════════════════════════════════════════════════════════════════

    /** Container for a tab + its editor + its document. */
    private static class TabEntry {
        final Tab tab;
        final EditorPane editor;
        final DocumentModel doc;

        TabEntry(Tab tab, EditorPane editor, DocumentModel doc) {
            this.tab    = tab;
            this.editor = editor;
            this.doc    = doc;
        }
    }
}
