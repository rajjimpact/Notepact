package com.notepact.editor;

import com.notepact.model.DocumentModel;
import com.notepact.search.SearchEngine;
import com.notepact.search.SearchResult;
import com.notepact.settings.SettingsManager;
import com.notepact.model.AppSettings;
import com.notepact.util.TextUtils;

import javafx.application.Platform;
import javafx.geometry.Insets;
import javafx.scene.control.*;
import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyEvent;
import javafx.scene.input.ScrollEvent;
import javafx.scene.layout.*;
import javafx.scene.paint.Color;
import javafx.scene.text.Font;

import org.fxmisc.flowless.VirtualizedScrollPane;
import org.fxmisc.richtext.CodeArea;
import org.fxmisc.richtext.LineNumberFactory;
import org.fxmisc.richtext.model.StyleSpans;

import java.time.Duration;
import java.util.*;
import java.util.concurrent.*;
import java.util.function.Consumer;
import java.util.logging.Logger;

/**
 * Core editor component wrapping a RichTextFX {@link CodeArea}.
 *
 * Features:
 * – Line numbers
 * – Real-time syntax highlighting (background thread)
 * – Active line highlight
 * – Word wrap
 * – Keyboard shortcuts for all line operations
 * – Find/replace highlight integration
 * – Zoom support
 */
public class EditorPane extends BorderPane {

    private static final Logger LOG = Logger.getLogger(EditorPane.class.getName());

    // ── State ─────────────────────────────────────────────────────────────────

    private final CodeArea codeArea;
    private final DocumentModel document;
    private final SearchEngine searchEngine = new SearchEngine();

    private final ExecutorService highlightExecutor = Executors.newSingleThreadExecutor(r -> {
        Thread t = new Thread(r, "SyntaxHighlight");
        t.setDaemon(true);
        return t;
    });

    private Consumer<DocumentModel> onModifiedCallback;
    private Consumer<Integer> onZoomCallback;
    private boolean settingContent = false;  // guard against recursive modification events

    // ── Constructor ───────────────────────────────────────────────────────────

    public EditorPane(DocumentModel document) {
        this.document = document;
        this.codeArea = new CodeArea();
        setupEditor();
        setupSyntaxHighlighting();
        setupKeyBindings();
        setupModificationTracking();
        applySettings();
    }

    // ── Setup ─────────────────────────────────────────────────────────────────

    private void setupEditor() {
        // Line numbers
        codeArea.setParagraphGraphicFactory(LineNumberFactory.get(codeArea));
        codeArea.getStyleClass().add("code-area");

        // Wrap in scroll-aware container
        VirtualizedScrollPane vsp = new VirtualizedScrollPane(codeArea);
        setCenter(vsp);
        VBox.setVgrow(vsp, Priority.ALWAYS);

        // Zoom via Ctrl + Scroll
        codeArea.addEventFilter(ScrollEvent.SCROLL, e -> {
            if (e.isControlDown()) {
                double delta = e.getDeltaY();
                if (delta != 0) {
                    if (onZoomCallback != null) {
                        onZoomCallback.accept(delta > 0 ? 1 : -1);
                    }
                }
                e.consume();
            }
        });
    }

    public void setOnCustomZoom(Consumer<Integer> callback) {
        this.onZoomCallback = callback;
    }

    private void setupSyntaxHighlighting() {
        String language = document.getLanguage();
        if (SyntaxHighlighter.fromString(language) == SyntaxHighlighter.Language.PLAIN_TEXT) return;

        // Subscribe to text changes with 100ms debounce, compute highlighting on background thread
        codeArea.multiPlainChanges()
                .successionEnds(Duration.ofMillis(100))
                .subscribe(change -> {
                    final String text = codeArea.getText();
                    highlightExecutor.submit(() -> {
                        StyleSpans<Collection<String>> spans =
                                SyntaxHighlighter.computeHighlighting(text, document.getLanguage());
                        Platform.runLater(() -> applyHighlighting(spans));
                    });
                });
    }

    private void applyHighlighting(StyleSpans<Collection<String>> highlighting) {
        if (highlighting != null && highlighting.length() <= codeArea.getLength()) {
            try { codeArea.setStyleSpans(0, highlighting); }
            catch (Exception ignored) {}
        }
    }

    private void setupModificationTracking() {
        codeArea.textProperty().addListener((obs, oldText, newText) -> {
            if (!settingContent) {
                document.setModified(true);
                document.setUnsavedContent(newText);
                if (onModifiedCallback != null) {
                    onModifiedCallback.accept(document);
                }
            }
        });
    }

    private void setupKeyBindings() {
        codeArea.addEventFilter(KeyEvent.KEY_PRESSED, this::handleKeyPress);
    }

    private void handleKeyPress(KeyEvent e) {
        boolean ctrl  = e.isControlDown();
        boolean shift = e.isShiftDown();
        boolean alt   = e.isAltDown();
        KeyCode k     = e.getCode();

        // Ctrl+D → Duplicate line
        if (ctrl && !shift && !alt && k == KeyCode.D) {
            duplicateLine(); e.consume();
        }
        // Ctrl+L → Delete line
        else if (ctrl && !shift && !alt && k == KeyCode.L) {
            deleteLine(); e.consume();
        }
        // Ctrl+J → Join lines
        else if (ctrl && !shift && !alt && k == KeyCode.J) {
            joinSelectedLines(); e.consume();
        }
        // Alt+Up → Move line up
        else if (!ctrl && !shift && alt && k == KeyCode.UP) {
            moveLine(-1); e.consume();
        }
        // Alt+Down → Move line down
        else if (!ctrl && !shift && alt && k == KeyCode.DOWN) {
            moveLine(1); e.consume();
        }
        // Shift+Alt+Up → Copy line up
        else if (!ctrl && shift && alt && k == KeyCode.UP) {
            copyLine(-1); e.consume();
        }
        // Shift+Alt+Down → Copy line down
        else if (!ctrl && shift && alt && k == KeyCode.DOWN) {
            copyLine(1); e.consume();
        }
        // Tab → indent with spaces if configured
        else if (!ctrl && !shift && !alt && k == KeyCode.TAB) {
            AppSettings s = SettingsManager.getInstance().getSettings();
            if (s.isInsertSpaces()) {
                codeArea.insertText(codeArea.getCaretPosition(),
                        " ".repeat(s.getTabSize()));
                e.consume();
            }
        }
    }

    // ── Line Operations ───────────────────────────────────────────────────────

    public void deleteLine() {
        int para = codeArea.getCurrentParagraph();
        int len  = codeArea.getParagraphLength(para);
        int start = codeArea.getAbsolutePosition(para, 0);
        int end;

        if (para < codeArea.getParagraphs().size() - 1) {
            // Include the trailing newline
            end = start + len + 1;
        } else {
            // Last line — also eat preceding newline
            end = start + len;
            if (start > 0) start--;
        }
        codeArea.replaceText(start, Math.min(end, codeArea.getLength()), "");
    }

    public void duplicateLine() {
        int para  = codeArea.getCurrentParagraph();
        String line = codeArea.getParagraph(para).getText();
        int absEnd  = codeArea.getAbsolutePosition(para, line.length());
        codeArea.insertText(absEnd, "\n" + line);
    }

    public void moveLine(int direction) {
        int para = codeArea.getCurrentParagraph();
        int total = codeArea.getParagraphs().size();

        if (direction < 0 && para == 0) return;
        if (direction > 0 && para == total - 1) return;

        String line     = codeArea.getParagraph(para).getText();
        int targetPara  = para + direction;
        String target   = codeArea.getParagraph(targetPara).getText();

        // Swap the two lines
        int lineStart   = codeArea.getAbsolutePosition(para, 0);
        int lineEnd     = lineStart + line.length();
        int targetStart = codeArea.getAbsolutePosition(targetPara, 0);
        int targetEnd   = targetStart + target.length();

        if (direction < 0) {
            // Replace target (above) with current line, then current with target
            codeArea.replaceText(targetStart, targetEnd, line);
            // Re-calc positions after first replacement
            int newLineStart = codeArea.getAbsolutePosition(para, 0);
            codeArea.replaceText(newLineStart, newLineStart + line.length(), target);
            codeArea.moveTo(targetPara, 0);
        } else {
            codeArea.replaceText(lineStart, lineEnd, target);
            int newTargetStart = codeArea.getAbsolutePosition(targetPara, 0);
            codeArea.replaceText(newTargetStart, newTargetStart + target.length(), line);
            codeArea.moveTo(targetPara, 0);
        }
    }

    public void copyLine(int direction) {
        int para  = codeArea.getCurrentParagraph();
        String line = codeArea.getParagraph(para).getText();
        int absEnd  = codeArea.getAbsolutePosition(para, line.length());

        if (direction < 0) {
            int absStart = codeArea.getAbsolutePosition(para, 0);
            codeArea.insertText(absStart, line + "\n");
        } else {
            codeArea.insertText(absEnd, "\n" + line);
        }
    }

    public void joinSelectedLines() {
        IndexRange sel = codeArea.getSelection();
        if (sel.getLength() == 0) {
            // Join current line with next
            int para = codeArea.getCurrentParagraph();
            if (para >= codeArea.getParagraphs().size() - 1) return;
            String cur  = codeArea.getParagraph(para).getText();
            int curEnd  = codeArea.getAbsolutePosition(para, cur.length());
            codeArea.replaceText(curEnd, curEnd + 1, " ");
        } else {
            String selected = codeArea.getSelectedText();
            String joined   = TextUtils.joinLines(selected);
            codeArea.replaceSelection(joined);
        }
    }

    // ── Text case operations ──────────────────────────────────────────────────

    public void transformSelection(java.util.function.UnaryOperator<String> op) {
        IndexRange sel = codeArea.getSelection();
        if (sel.getLength() == 0) return;
        String text    = codeArea.getSelectedText();
        String result  = op.apply(text);
        codeArea.replaceSelection(result);
    }

    // ── Sort / Dedup ──────────────────────────────────────────────────────────

    public void sortLines(boolean ascending, boolean numeric) {
        String text = getAllText();
        String sorted = numeric
                ? (ascending ? TextUtils.sortLinesNumericAsc(text) : TextUtils.sortLinesNumericDesc(text))
                : (ascending ? TextUtils.sortLinesAZ(text) : TextUtils.sortLinesZA(text));
        replaceAllText(sorted);
    }

    public void removeEmptyLines() {
        replaceAllText(TextUtils.removeEmptyLines(getAllText()));
    }

    public void removeDuplicateLines() {
        replaceAllText(TextUtils.removeDuplicateLines(getAllText()));
    }

    public void trimLines(boolean leading, boolean trailing) {
        String text = getAllText();
        if (leading && trailing) text = TextUtils.trimAll(text);
        else if (leading)        text = TextUtils.trimLeading(text);
        else if (trailing)       text = TextUtils.trimTrailing(text);
        replaceAllText(text);
    }

    // ── Find highlight ────────────────────────────────────────────────────────

    public void highlightMatches(List<SearchResult> results) {
        // Clear existing search highlights
        codeArea.setStyleClass(0, codeArea.getLength(), "");
        for (SearchResult r : results) {
            if (r.getEnd() <= codeArea.getLength()) {
                codeArea.setStyleClass(r.getStart(), r.getEnd(), "search-match");
            }
        }
    }

    public void clearSearchHighlights() {
        // Re-trigger syntax highlighting
        codeArea.setStyleClass(0, codeArea.getLength(), "");
    }

    public void scrollToResult(SearchResult result) {
        if (result == null) return;
        Platform.runLater(() -> {
            codeArea.selectRange(result.getStart(), result.getEnd());
            codeArea.showParagraphAtTop(Math.max(0, result.getLineNumber() - 5));
        });
    }

    // ── Bookmarks ─────────────────────────────────────────────────────────────

    public void scrollToLine(int lineNumber) {
        int para = Math.max(0, Math.min(lineNumber - 1, codeArea.getParagraphs().size() - 1));
        codeArea.moveTo(para, 0);
        codeArea.showParagraphAtTop(Math.max(0, para - 3));
    }

    // ── Content Access ────────────────────────────────────────────────────────

    public String getAllText() { return codeArea.getText(); }

    public void setContent(String text) {
        settingContent = true;
        try {
            codeArea.replaceText(text == null ? "" : text);
            codeArea.moveTo(0);
            codeArea.getUndoManager().forgetHistory();
            document.setModified(false);
        } finally {
            settingContent = false;
        }
    }

    private void replaceAllText(String text) {
        int caret = codeArea.getCaretPosition();
        codeArea.replaceText(text);
        int newCaret = Math.min(caret, codeArea.getLength());
        codeArea.moveTo(newCaret);
    }

    // ── Settings application ──────────────────────────────────────────────────

    public void applySettings() {
        AppSettings s = SettingsManager.getInstance().getSettings();

        // Font
        double size = s.getFontSize() * (s.getZoomLevel() / 100.0);
        codeArea.setStyle(
            "-fx-font-family: '" + s.getFontFamily() + "';" +
            "-fx-font-size: " + size + "px;"
        );

        // Word wrap
        codeArea.setWrapText(s.isWordWrap());
    }

    public void setZoom(int percent) {
        AppSettings s = SettingsManager.getInstance().getSettings();
        double size = s.getFontSize() * (percent / 100.0);
        codeArea.setStyle(
            "-fx-font-family: '" + s.getFontFamily() + "';" +
            "-fx-font-size: " + size + "px;"
        );
    }

    public void setWordWrap(boolean wrap) {
        codeArea.setWrapText(wrap);
    }

    // ── Status bar data ───────────────────────────────────────────────────────

    public int getCurrentLine()   { return codeArea.getCurrentParagraph() + 1; }
    public int getCurrentColumn() { return codeArea.getCaretColumn() + 1; }
    public int getCharCount()     { return codeArea.getLength(); }
    public int getWordCount()     { return TextUtils.countWords(codeArea.getText()); }
    public int getSelectionLength() { return codeArea.getSelection().getLength(); }

    // ── Undo/Redo ─────────────────────────────────────────────────────────────

    public void undo() { codeArea.undo(); }
    public void redo() { codeArea.redo(); }
    public boolean canUndo() { return codeArea.isUndoAvailable(); }
    public boolean canRedo() { return codeArea.isRedoAvailable(); }

    // ── Clipboard ─────────────────────────────────────────────────────────────

    public void cut()   { codeArea.cut(); }
    public void copy()  { codeArea.copy(); }
    public void paste() { codeArea.paste(); }
    public void selectAll() { codeArea.selectAll(); }

    // ── Focus ─────────────────────────────────────────────────────────────────

    public void focusEditor() {
        Platform.runLater(codeArea::requestFocus);
    }

    // ── Getters ───────────────────────────────────────────────────────────────

    public CodeArea getCodeArea()        { return codeArea; }
    public DocumentModel getDocument()   { return document; }
    public SearchEngine getSearchEngine(){ return searchEngine; }

    public void setOnModified(Consumer<DocumentModel> cb) {
        this.onModifiedCallback = cb;
    }

    // ── Lifecycle ─────────────────────────────────────────────────────────────

    public void dispose() {
        highlightExecutor.shutdown();
    }
}
