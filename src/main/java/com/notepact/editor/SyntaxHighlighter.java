package com.notepact.editor;

import org.fxmisc.richtext.model.StyleSpans;
import org.fxmisc.richtext.model.StyleSpansBuilder;

import java.util.*;
import java.util.regex.*;

/**
 * Regex-based multi-language syntax highlighter for RichTextFX CodeArea.
 *
 * Returns {@code StyleSpans<Collection<String>>} where each span's collection
 * contains a single CSS class name (e.g. "keyword", "string", "comment").
 */
public class SyntaxHighlighter {

    // ── Java ──────────────────────────────────────────────────────────────────

    private static final String[] JAVA_KW = {
        "abstract","assert","boolean","break","byte","case","catch","char",
        "class","const","continue","default","do","double","else","enum",
        "extends","final","finally","float","for","goto","if","implements",
        "import","instanceof","int","interface","long","native","new",
        "package","private","protected","public","record","return","sealed",
        "short","static","strictfp","super","switch","synchronized","this",
        "throw","throws","transient","try","var","void","volatile","while",
        "permits","yield","true","false","null"
    };

    // ── Python ────────────────────────────────────────────────────────────────

    private static final String[] PYTHON_KW = {
        "and","as","assert","async","await","break","class","continue",
        "def","del","elif","else","except","finally","for","from","global",
        "if","import","in","is","lambda","nonlocal","not","or","pass",
        "raise","return","try","while","with","yield","True","False","None"
    };

    // ── JavaScript ────────────────────────────────────────────────────────────

    private static final String[] JS_KW = {
        "abstract","arguments","await","boolean","break","byte","case",
        "catch","char","class","const","continue","debugger","default",
        "delete","do","double","else","enum","eval","export","extends",
        "false","final","finally","float","for","function","goto","if",
        "implements","import","in","instanceof","int","interface","let",
        "long","native","new","null","package","private","protected",
        "public","return","short","static","super","switch","synchronized",
        "this","throw","throws","transient","true","try","typeof","undefined",
        "var","void","volatile","while","with","yield","async","of","from"
    };

    // ── SQL ───────────────────────────────────────────────────────────────────

    private static final String[] SQL_KW = {
        "SELECT","FROM","WHERE","INSERT","INTO","VALUES","UPDATE","SET",
        "DELETE","CREATE","TABLE","DROP","ALTER","ADD","COLUMN","INDEX",
        "VIEW","DATABASE","SCHEMA","JOIN","LEFT","RIGHT","INNER","OUTER",
        "FULL","ON","AND","OR","NOT","IN","IS","NULL","LIKE","BETWEEN",
        "GROUP","BY","ORDER","HAVING","LIMIT","OFFSET","DISTINCT","AS",
        "COUNT","SUM","AVG","MAX","MIN","PRIMARY","KEY","FOREIGN","UNIQUE",
        "CONSTRAINT","DEFAULT","AUTO_INCREMENT","COMMIT","ROLLBACK","BEGIN",
        "TRANSACTION","GRANT","REVOKE","UNION","EXCEPT","INTERSECT","CASE",
        "WHEN","THEN","ELSE","END","EXISTS","ALL","ANY","SOME"
    };

    // ── HTML tags ─────────────────────────────────────────────────────────────

    private static final String[] HTML_TAGS = {
        "html","head","body","title","meta","link","script","style","div",
        "span","p","a","img","br","hr","h1","h2","h3","h4","h5","h6",
        "ul","ol","li","table","tr","td","th","thead","tbody","tfoot",
        "form","input","button","select","option","textarea","label",
        "section","article","nav","header","footer","main","aside",
        "figure","figcaption","canvas","svg","video","audio","iframe",
        "pre","code","blockquote","em","strong","b","i","u","s","del",
        "ins","sub","sup","small","abbr","cite","q","mark","details","summary"
    };

    // ── YAML keywords ─────────────────────────────────────────────────────────

    private static final String[] YAML_KW = {
        "true","false","null","yes","no","on","off"
    };

    // ── Language enum ─────────────────────────────────────────────────────────

    public enum Language {
        JAVA, PYTHON, JAVASCRIPT, HTML, CSS, XML, JSON, SQL, MARKDOWN, YAML, PLAIN_TEXT
    }

    // ── Pattern cache ─────────────────────────────────────────────────────────

    private static final Map<Language, Pattern> PATTERN_CACHE = new EnumMap<>(Language.class);

    static {
        PATTERN_CACHE.put(Language.JAVA,       buildJavaPattern());
        PATTERN_CACHE.put(Language.PYTHON,     buildPythonPattern());
        PATTERN_CACHE.put(Language.JAVASCRIPT, buildJsPattern());
        PATTERN_CACHE.put(Language.HTML,       buildHtmlPattern());
        PATTERN_CACHE.put(Language.CSS,        buildCssPattern());
        PATTERN_CACHE.put(Language.XML,        buildXmlPattern());
        PATTERN_CACHE.put(Language.JSON,       buildJsonPattern());
        PATTERN_CACHE.put(Language.SQL,        buildSqlPattern());
        PATTERN_CACHE.put(Language.MARKDOWN,   buildMarkdownPattern());
        PATTERN_CACHE.put(Language.YAML,       buildYamlPattern());
    }

    // ── Pattern builders ──────────────────────────────────────────────────────

    private static Pattern buildJavaPattern() {
        return Pattern.compile(
            "(?<COMMENT>//[^\n]*|/\\*.*?\\*/)" +
            "|(?<STRING>\"(?:[^\"\\\\]|\\\\.)*\"|'(?:[^'\\\\]|\\\\.)*')" +
            "|(?<ANNOTATION>@\\w+)" +
            "|(?<KEYWORD>\\b(" + String.join("|", JAVA_KW) + ")\\b)" +
            "|(?<NUMBER>\\b\\d+\\.?\\d*[fFdDlL]?\\b|0[xX][0-9a-fA-F]+)" +
            "|(?<PAREN>[()])" +
            "|(?<BRACE>[{}])" +
            "|(?<BRACKET>[\\[\\]])" +
            "|(?<SEMICOLON>[;,])",
            Pattern.DOTALL | Pattern.MULTILINE);
    }

    private static Pattern buildPythonPattern() {
        return Pattern.compile(
            "(?<COMMENT>#[^\n]*)" +
            "|(?<DOCSTRING>\"\"\".*?\"\"\"|'''.*?''')" +
            "|(?<STRING>\"(?:[^\"\\\\]|\\\\.)*\"|'(?:[^'\\\\]|\\\\.)*')" +
            "|(?<DECORATOR>@\\w+)" +
            "|(?<KEYWORD>\\b(" + String.join("|", PYTHON_KW) + ")\\b)" +
            "|(?<BUILTIN>\\b(print|len|range|type|int|str|float|list|dict|set|tuple|bool|input|open|super|property)\\b)" +
            "|(?<NUMBER>\\b\\d+\\.?\\d*j?\\b|0[xX][0-9a-fA-F]+|0[oO][0-7]+|0[bB][01]+)" +
            "|(?<PAREN>[()])" +
            "|(?<BRACE>[{}])" +
            "|(?<BRACKET>[\\[\\]])",
            Pattern.DOTALL | Pattern.MULTILINE);
    }

    private static Pattern buildJsPattern() {
        return Pattern.compile(
            "(?<COMMENT>//[^\n]*|/\\*.*?\\*/)" +
            "|(?<TEMPLATE>`(?:[^`\\\\]|\\\\.)*`)" +
            "|(?<STRING>\"(?:[^\"\\\\]|\\\\.)*\"|'(?:[^'\\\\]|\\\\.)*')" +
            "|(?<KEYWORD>\\b(" + String.join("|", JS_KW) + ")\\b)" +
            "|(?<NUMBER>\\b\\d+\\.?\\d*\\b|0[xX][0-9a-fA-F]+)" +
            "|(?<PAREN>[()])" +
            "|(?<BRACE>[{}])" +
            "|(?<BRACKET>[\\[\\]])" +
            "|(?<ARROW>=>)",
            Pattern.DOTALL | Pattern.MULTILINE);
    }

    private static Pattern buildHtmlPattern() {
        return Pattern.compile(
            "(?<COMMENT><!--.*?-->)" +
            "|(?<CDATA><!\\[CDATA\\[.*?\\]\\]>)" +
            "|(?<DOCTYPE><!DOCTYPE[^>]*>)" +
            "|(?<TAG></?(?:" + String.join("|", HTML_TAGS) + ")(?=[\\s>])[^>]*>)" +
            "|(?<ATTRIB>\\s[a-zA-Z-:]+(?==))" +
            "|(?<STRING>\"[^\"]*\"|'[^']*')" +
            "|(?<ENTITY>&[a-zA-Z#0-9]+;)",
            Pattern.DOTALL | Pattern.MULTILINE | Pattern.CASE_INSENSITIVE);
    }

    private static Pattern buildCssPattern() {
        return Pattern.compile(
            "(?<COMMENT>/\\*.*?\\*/)" +
            "|(?<SELECTOR>[.#]?[a-zA-Z][a-zA-Z0-9_-]*(?=\\s*\\{))" +
            "|(?<PROPERTY>[a-z-]+(?=\\s*:))" +
            "|(?<VALUE>:\\s*[^;}{]+)" +
            "|(?<STRING>\"[^\"]*\"|'[^']*')" +
            "|(?<ATRULE>@[a-zA-Z-]+)" +
            "|(?<IMPORTANT>!important)" +
            "|(?<NUMBER>\\b\\d+\\.?\\d*(?:px|em|rem|%|vh|vw|pt|pc|cm|mm|in|s|ms)?\\b)",
            Pattern.DOTALL | Pattern.MULTILINE);
    }

    private static Pattern buildXmlPattern() {
        return Pattern.compile(
            "(?<COMMENT><!--.*?-->)" +
            "|(?<CDATA><!\\[CDATA\\[.*?\\]\\]>)" +
            "|(?<PROLOG><\\?[^?]*\\?>)" +
            "|(?<CLOSETAG></[a-zA-Z][a-zA-Z0-9:_-]*>)" +
            "|(?<OPENTAG><[a-zA-Z][a-zA-Z0-9:_-]*)" +
            "|(?<TAGEND>/?\\s*>)" +
            "|(?<ATTRIB>[a-zA-Z:_][a-zA-Z0-9:_.-]*(?==))" +
            "|(?<STRING>\"[^\"]*\"|'[^']*')" +
            "|(?<ENTITY>&[a-zA-Z#0-9]+;)",
            Pattern.DOTALL | Pattern.MULTILINE);
    }

    private static Pattern buildJsonPattern() {
        return Pattern.compile(
            "(?<STRING>\"(?:[^\"\\\\]|\\\\.)*\")" +
            "|(?<NUMBER>-?\\d+\\.?\\d*(?:[eE][+-]?\\d+)?)" +
            "|(?<BOOLEAN>\\btrue\\b|\\bfalse\\b)" +
            "|(?<NULL>\\bnull\\b)" +
            "|(?<BRACE>[{}])" +
            "|(?<BRACKET>[\\[\\]])" +
            "|(?<COLON>:)" +
            "|(?<COMMA>,)",
            Pattern.DOTALL | Pattern.MULTILINE);
    }

    private static Pattern buildSqlPattern() {
        return Pattern.compile(
            "(?<COMMENT>--[^\n]*|/\\*.*?\\*/)" +
            "|(?<STRING>'(?:[^'\\\\]|\\\\.)*'|\"(?:[^\"\\\\]|\\\\.)*\")" +
            "|(?<KEYWORD>\\b(?i)(" + String.join("|", SQL_KW) + ")\\b)" +
            "|(?<NUMBER>\\b\\d+\\.?\\d*\\b)" +
            "|(?<PAREN>[()])" +
            "|(?<SEMICOLON>[;])",
            Pattern.DOTALL | Pattern.MULTILINE);
    }

    private static Pattern buildMarkdownPattern() {
        return Pattern.compile(
            "(?<HEADING>^#{1,6}\\s+.+$)" +
            "|(?<BOLD>\\*\\*[^*]+\\*\\*|__[^_]+__)" +
            "|(?<ITALIC>\\*[^*]+\\*|_[^_]+_)" +
            "|(?<CODE>`{1,3}[^`]+`{1,3})" +
            "|(?<LINK>\\[.+?\\]\\(.+?\\))" +
            "|(?<IMAGE>!\\[.+?\\]\\(.+?\\))" +
            "|(?<BLOCKQUOTE>^>\\s+.+$)" +
            "|(?<LISTITEM>^\\s*[-*+]\\s|^\\s*\\d+\\.\\s)" +
            "|(?<HR>^---+$|^===+$)" +
            "|(?<URL>https?://\\S+)",
            Pattern.MULTILINE);
    }

    private static Pattern buildYamlPattern() {
        return Pattern.compile(
            "(?<COMMENT>#[^\n]*)" +
            "|(?<KEY>^\\s*[a-zA-Z_][a-zA-Z0-9_-]*(?=\\s*:))" +
            "|(?<STRING>\"(?:[^\"\\\\]|\\\\.)*\"|'(?:[^'\\\\]|\\\\.)*')" +
            "|(?<KEYWORD>\\b(" + String.join("|", YAML_KW) + ")\\b)" +
            "|(?<NUMBER>\\b-?\\d+\\.?\\d*\\b)" +
            "|(?<ANCHOR>&[a-zA-Z_][a-zA-Z0-9_]*)" +
            "|(?<ALIAS>\\*[a-zA-Z_][a-zA-Z0-9_]*)" +
            "|(?<TAG>!!?[a-zA-Z][a-zA-Z0-9]*)",
            Pattern.MULTILINE);
    }

    // ── Public API ────────────────────────────────────────────────────────────

    /**
     * Detects the language from the file name and computes highlighting.
     */
    public static StyleSpans<Collection<String>> computeHighlighting(String text, String language) {
        Language lang = fromString(language);
        return computeHighlighting(text, lang);
    }

    public static StyleSpans<Collection<String>> computeHighlighting(String text, Language lang) {
        if (lang == Language.PLAIN_TEXT || text == null || text.isEmpty()) {
            return StyleSpans.singleton(Collections.emptyList(), text == null ? 0 : text.length());
        }

        Pattern pattern = PATTERN_CACHE.get(lang);
        if (pattern == null) {
            return StyleSpans.singleton(Collections.emptyList(), text.length());
        }

        StyleSpansBuilder<Collection<String>> spansBuilder = new StyleSpansBuilder<>();
        Matcher matcher = pattern.matcher(text);
        int lastEnd = 0;

        while (matcher.find()) {
            String styleClass = getStyleClass(matcher);
            spansBuilder.add(Collections.emptyList(), matcher.start() - lastEnd);
            spansBuilder.add(Collections.singleton(styleClass), matcher.end() - matcher.start());
            lastEnd = matcher.end();
        }
        spansBuilder.add(Collections.emptyList(), text.length() - lastEnd);
        return spansBuilder.create();
    }

    private static String getStyleClass(Matcher m) {
        String[] groups = {
            "COMMENT","DOCSTRING","TEMPLATE","STRING","KEYWORD","BUILTIN","ANNOTATION",
            "DECORATOR","NUMBER","BOOLEAN","NULL","PAREN","BRACE","BRACKET","SEMICOLON",
            "COLON","COMMA","ARROW","TAG","CLOSETAG","OPENTAG","TAGEND","ATTRIB",
            "ENTITY","PROPERTY","VALUE","SELECTOR","ATRULE","IMPORTANT","CDATA",
            "DOCTYPE","PROLOG","HEADING","BOLD","ITALIC","CODE","LINK","IMAGE",
            "BLOCKQUOTE","LISTITEM","HR","URL","KEY","ANCHOR","ALIAS",
            "BUILTIN","COMMENT"
        };
        for (String g : groups) {
            try {
                if (m.group(g) != null) return g.toLowerCase();
            } catch (IllegalArgumentException ignored) {}
        }
        return "other";
    }

    public static Language fromString(String lang) {
        if (lang == null) return Language.PLAIN_TEXT;
        return switch (lang.toLowerCase()) {
            case "java"                   -> Language.JAVA;
            case "python"                 -> Language.PYTHON;
            case "javascript","typescript"-> Language.JAVASCRIPT;
            case "html"                   -> Language.HTML;
            case "css"                    -> Language.CSS;
            case "xml"                    -> Language.XML;
            case "json"                   -> Language.JSON;
            case "sql"                    -> Language.SQL;
            case "markdown"               -> Language.MARKDOWN;
            case "yaml"                   -> Language.YAML;
            default                       -> Language.PLAIN_TEXT;
        };
    }
}
