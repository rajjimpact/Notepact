package com.notepact.search;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.*;

/**
 * High-performance text search and replace engine.
 * Supports: literal, case-insensitive, whole-word, and regex modes.
 */
public class SearchEngine {

    // ── Options (mutable, set by find-bar) ────────────────────────────────────

    private boolean caseSensitive = false;
    private boolean wholeWord     = false;
    private boolean useRegex      = false;

    private String  lastQuery   = "";
    private Pattern lastPattern = null;

    // ── Configuration ─────────────────────────────────────────────────────────

    public void setCaseSensitive(boolean v) { this.caseSensitive = v; lastPattern = null; }
    public void setWholeWord(boolean v)     { this.wholeWord = v;     lastPattern = null; }
    public void setUseRegex(boolean v)      { this.useRegex = v;      lastPattern = null; }

    public boolean isCaseSensitive() { return caseSensitive; }
    public boolean isWholeWord()     { return wholeWord; }
    public boolean isUseRegex()      { return useRegex; }

    // ── Core Search ───────────────────────────────────────────────────────────

    /**
     * Find all occurrences of {@code query} in {@code text}.
     * Returns list ordered by position.
     */
    public List<SearchResult> findAll(String text, String query) {
        List<SearchResult> results = new ArrayList<>();
        if (query == null || query.isEmpty() || text == null || text.isEmpty()) return results;

        Pattern p = buildPattern(query);
        if (p == null) return results;

        Matcher m = p.matcher(text);
        while (m.find()) {
            int lineNum = countNewlines(text, 0, m.start()) + 1;
            results.add(new SearchResult(m.start(), m.end(), m.group(), lineNum));
        }
        return results;
    }

    /**
     * Find the next match after {@code fromPos}.
     * Wraps around to the beginning if none found after.
     */
    public SearchResult findNext(String text, String query, int fromPos) {
        if (query == null || query.isEmpty() || text == null) return null;
        Pattern p = buildPattern(query);
        if (p == null) return null;

        Matcher m = p.matcher(text);

        // Try from fromPos first
        while (m.find()) {
            if (m.start() >= fromPos) {
                int ln = countNewlines(text, 0, m.start()) + 1;
                return new SearchResult(m.start(), m.end(), m.group(), ln);
            }
        }
        // Wrap around
        m.reset();
        if (m.find()) {
            int ln = countNewlines(text, 0, m.start()) + 1;
            return new SearchResult(m.start(), m.end(), m.group(), ln);
        }
        return null;
    }

    /**
     * Find previous match before {@code fromPos}.
     */
    public SearchResult findPrevious(String text, String query, int fromPos) {
        if (query == null || query.isEmpty() || text == null) return null;
        Pattern p = buildPattern(query);
        if (p == null) return null;

        Matcher m = p.matcher(text);
        SearchResult last = null;
        while (m.find()) {
            if (m.end() <= fromPos) {
                int ln = countNewlines(text, 0, m.start()) + 1;
                last = new SearchResult(m.start(), m.end(), m.group(), ln);
            }
        }
        if (last != null) return last;

        // Wrap — find last match in whole text
        m.reset();
        while (m.find()) {
            int ln = countNewlines(text, 0, m.start()) + 1;
            last = new SearchResult(m.start(), m.end(), m.group(), ln);
        }
        return last;
    }

    // ── Replace ───────────────────────────────────────────────────────────────

    /**
     * Replace a single occurrence (the match at {@code result}) with {@code replacement}.
     */
    public String replaceOne(String text, SearchResult result, String replacement) {
        if (result == null) return text;
        return text.substring(0, result.getStart())
                + applyReplacement(result.getMatchedText(), replacement)
                + text.substring(result.getEnd());
    }

    /**
     * Replace all occurrences of {@code query} with {@code replacement}.
     * Returns the modified text.
     */
    public String replaceAll(String text, String query, String replacement) {
        if (query == null || query.isEmpty()) return text;
        Pattern p = buildPattern(query);
        if (p == null) return text;
        try {
            return p.matcher(text).replaceAll(
                    useRegex ? replacement : Matcher.quoteReplacement(replacement));
        } catch (Exception e) {
            return text;
        }
    }

    // ── Pattern Builder ───────────────────────────────────────────────────────

    private Pattern buildPattern(String query) {
        if (query.equals(lastQuery) && lastPattern != null) return lastPattern;
        lastQuery = query;
        try {
            String expr = useRegex ? query : Pattern.quote(query);
            if (wholeWord) expr = "\\b" + expr + "\\b";
            int flags = caseSensitive ? 0 : Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE;
            lastPattern = Pattern.compile(expr, flags);
            return lastPattern;
        } catch (PatternSyntaxException e) {
            lastPattern = null;
            return null;
        }
    }

    private String applyReplacement(String matched, String replacement) {
        return useRegex ? replacement : replacement;
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private int countNewlines(String text, int from, int to) {
        int count = 0;
        for (int i = from; i < to && i < text.length(); i++) {
            if (text.charAt(i) == '\n') count++;
        }
        return count;
    }

    public boolean isValidPattern(String query) {
        if (!useRegex) return true;
        try {
            Pattern.compile(query);
            return true;
        } catch (PatternSyntaxException e) {
            return false;
        }
    }
}
