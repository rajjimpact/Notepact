package com.notepact.controller;

import com.notepact.search.SearchEngine;
import com.notepact.search.SearchResult;
import com.notepact.editor.EditorPane;
import com.notepact.settings.SettingsManager;

import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.*;
import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyEvent;
import javafx.scene.layout.*;
import javafx.scene.paint.Color;

import java.util.List;

/**
 * Find & Replace bar embedded above the editor area.
 * Shows/hides dynamically. Supports all search modes.
 */
public class FindReplaceBar extends HBox {

    private final MainController controller;

    private final TextField     tfFind;
    private final TextField     tfReplace;
    private final CheckBox      cbCase;
    private final CheckBox      cbWholeWord;
    private final CheckBox      cbRegex;
    private final Label         lblCount;
    private final Button        btnPrev, btnNext, btnReplaceOne, btnReplaceAll, btnClose;
    private final HBox          replaceRow;

    private final SearchEngine  engine = new SearchEngine();
    private List<SearchResult>  results = List.of();
    private int                 currentIndex = -1;

    private boolean replaceMode = false;

    // ── Constructor ───────────────────────────────────────────────────────────

    public FindReplaceBar(MainController controller) {
        this.controller = controller;
        getStyleClass().add("find-bar");
        setSpacing(8);
        setAlignment(Pos.CENTER_LEFT);
        setPadding(new Insets(6, 10, 6, 10));

        // ── Find row ──────────────────────────────────────────────────────────
        tfFind = new TextField();
        tfFind.setPromptText("Find...");
        tfFind.getStyleClass().add("find-field");
        tfFind.setPrefWidth(220);

        cbCase      = new CheckBox("Aa");
        cbWholeWord = new CheckBox("\\b");
        cbRegex     = new CheckBox(".*");
        cbCase.getStyleClass().add("search-check");
        cbWholeWord.getStyleClass().add("search-check");
        cbRegex.getStyleClass().add("search-check");
        cbCase.setTooltip(new Tooltip("Case Sensitive"));
        cbWholeWord.setTooltip(new Tooltip("Whole Word"));
        cbRegex.setTooltip(new Tooltip("Regular Expression"));

        lblCount = new Label("");
        lblCount.getStyleClass().add("find-count");
        lblCount.setMinWidth(80);

        btnPrev = findBtn("◀", "Find Previous (Shift+F3)");
        btnNext = findBtn("▶", "Find Next (F3)");
        btnPrev.setOnAction(e -> findPrevious());
        btnNext.setOnAction(e -> findNext());

        // ── Replace row ───────────────────────────────────────────────────────
        tfReplace = new TextField();
        tfReplace.setPromptText("Replace with...");
        tfReplace.getStyleClass().add("find-field");
        tfReplace.setPrefWidth(200);

        btnReplaceOne = findBtn("Replace", "Replace current match");
        btnReplaceAll = findBtn("Replace All", "Replace all matches");
        btnReplaceOne.setOnAction(e -> replaceOne());
        btnReplaceAll.setOnAction(e -> replaceAll());

        replaceRow = new HBox(6, tfReplace, btnReplaceOne, btnReplaceAll);
        replaceRow.setAlignment(Pos.CENTER_LEFT);
        replaceRow.setVisible(false);
        replaceRow.setManaged(false);

        // ── Close button ──────────────────────────────────────────────────────
        btnClose = new Button("✕");
        btnClose.getStyleClass().add("find-close-btn");
        btnClose.setOnAction(e -> controller.closeFindBar());

        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);

        // ── Layout ────────────────────────────────────────────────────────────
        VBox searchStack = new VBox(4,
            new HBox(6, tfFind, btnPrev, btnNext, cbCase, cbWholeWord, cbRegex, lblCount),
            replaceRow
        );
        ((HBox) searchStack.getChildren().get(0)).setAlignment(Pos.CENTER_LEFT);

        getChildren().addAll(searchStack, spacer, btnClose);

        // ── Listeners ─────────────────────────────────────────────────────────
        tfFind.textProperty().addListener((obs, o, n) -> performSearch());
        cbCase.selectedProperty().addListener((obs, o, n) -> performSearch());
        cbWholeWord.selectedProperty().addListener((obs, o, n) -> performSearch());
        cbRegex.selectedProperty().addListener((obs, o, n) -> performSearch());

        tfFind.addEventHandler(KeyEvent.KEY_PRESSED, e -> {
            if (e.getCode() == KeyCode.ENTER) {
                if (e.isShiftDown()) findPrevious();
                else findNext();
                e.consume();
            }
            if (e.getCode() == KeyCode.ESCAPE) {
                controller.closeFindBar();
            }
        });

        tfReplace.addEventHandler(KeyEvent.KEY_PRESSED, e -> {
            if (e.getCode() == KeyCode.ENTER) { replaceOne(); e.consume(); }
        });

        // Sync settings from SettingsManager
        var s = SettingsManager.getInstance().getSettings();
        cbCase.setSelected(s.isSearchCaseSensitive());
        cbWholeWord.setSelected(s.isSearchWholeWord());
        cbRegex.setSelected(s.isSearchRegex());
    }

    // ── Search ────────────────────────────────────────────────────────────────

    private void performSearch() {
        updateEngine();
        String query = tfFind.getText();

        getActiveEditor().ifPresent(ep -> {
            if (query.isEmpty()) {
                results = List.of();
                currentIndex = -1;
                lblCount.setText("");
                ep.clearSearchHighlights();
                tfFind.setStyle("");
                return;
            }

            results = engine.findAll(ep.getAllText(), query);
            currentIndex = results.isEmpty() ? -1 : 0;
            ep.highlightMatches(results);

            if (results.isEmpty()) {
                lblCount.setText("No results");
                tfFind.setStyle("-fx-border-color: #cc3333;");
            } else {
                updateCount();
                tfFind.setStyle("-fx-border-color: #4ec9b0;");
                if (currentIndex >= 0) ep.scrollToResult(results.get(currentIndex));
            }
        });
    }

    public void findNext() {
        if (results.isEmpty()) { performSearch(); return; }
        currentIndex = (currentIndex + 1) % results.size();
        getActiveEditor().ifPresent(ep -> ep.scrollToResult(results.get(currentIndex)));
        updateCount();
    }

    public void findPrevious() {
        if (results.isEmpty()) { performSearch(); return; }
        currentIndex = (currentIndex - 1 + results.size()) % results.size();
        getActiveEditor().ifPresent(ep -> ep.scrollToResult(results.get(currentIndex)));
        updateCount();
    }

    // ── Replace ───────────────────────────────────────────────────────────────

    private void replaceOne() {
        if (results.isEmpty() || currentIndex < 0) return;
        getActiveEditor().ifPresent(ep -> {
            SearchResult r = results.get(currentIndex);
            String newText = engine.replaceOne(ep.getAllText(), r, tfReplace.getText());
            ep.setContent(newText);
            performSearch();
        });
    }

    private void replaceAll() {
        if (tfFind.getText().isEmpty()) return;
        updateEngine();
        getActiveEditor().ifPresent(ep -> {
            String newText = engine.replaceAll(ep.getAllText(), tfFind.getText(), tfReplace.getText());
            ep.setContent(newText);
            performSearch();
        });
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private void updateEngine() {
        engine.setCaseSensitive(cbCase.isSelected());
        engine.setWholeWord(cbWholeWord.isSelected());
        engine.setUseRegex(cbRegex.isSelected());
    }

    private void updateCount() {
        if (results.isEmpty()) lblCount.setText("No results");
        else lblCount.setText((currentIndex + 1) + " / " + results.size());
    }

    private java.util.Optional<EditorPane> getActiveEditor() {
        return controller.activeEditor_public();
    }

    public void setReplaceMode(boolean replace) {
        this.replaceMode = replace;
        replaceRow.setVisible(replace);
        replaceRow.setManaged(replace);
    }

    public void focusSearch() {
        tfFind.requestFocus();
        tfFind.selectAll();
    }

    public boolean isReplaceMode() { return replaceMode; }

    private Button findBtn(String text, String tooltip) {
        Button b = new Button(text);
        b.getStyleClass().add("find-button");
        b.setTooltip(new Tooltip(tooltip));
        return b;
    }

    public java.util.Optional<EditorPane> activeEditor() {
        return getActiveEditor();
    }
}
