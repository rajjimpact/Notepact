package com.notepact.search;

/**
 * Represents a single search match result within a document.
 */
public class SearchResult {

    private final int start;
    private final int end;
    private final String matchedText;
    private final int lineNumber;

    public SearchResult(int start, int end, String matchedText, int lineNumber) {
        this.start       = start;
        this.end         = end;
        this.matchedText = matchedText;
        this.lineNumber  = lineNumber;
    }

    public int getStart()       { return start; }
    public int getEnd()         { return end; }
    public String getMatchedText() { return matchedText; }
    public int getLineNumber()  { return lineNumber; }
    public int getLength()      { return end - start; }

    @Override
    public String toString() {
        return "Line " + lineNumber + " [" + start + "-" + end + "]: " + matchedText;
    }
}
