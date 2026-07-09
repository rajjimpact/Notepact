package com.notepact.util;

import java.util.*;
import java.util.regex.*;
import java.util.stream.Collectors;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

/**
 * Utility class for all text transformation operations.
 * Covers: case conversion, trimming, sorting, line operations, statistics.
 */
public final class TextUtils {

    private TextUtils() {}

    // ── Case Conversion ───────────────────────────────────────────────────────

    public static String toUpperCase(String text) {
        return text.toUpperCase();
    }

    public static String toLowerCase(String text) {
        return text.toLowerCase();
    }

    public static String toTitleCase(String text) {
        if (text == null || text.isEmpty()) return text;
        StringBuilder sb = new StringBuilder();
        boolean nextUpper = true;
        for (char c : text.toCharArray()) {
            if (Character.isWhitespace(c)) {
                nextUpper = true;
                sb.append(c);
            } else {
                sb.append(nextUpper ? Character.toUpperCase(c) : Character.toLowerCase(c));
                nextUpper = false;
            }
        }
        return sb.toString();
    }

    public static String toSentenceCase(String text) {
        if (text == null || text.isEmpty()) return text;
        String lower = text.toLowerCase();
        // Capitalize first letter of each sentence
        Pattern p = Pattern.compile("(^|[.!?]\\s+)([a-z])");
        Matcher m = p.matcher(lower);
        StringBuilder sb = new StringBuilder();
        while (m.find()) {
            m.appendReplacement(sb, m.group(1) + m.group(2).toUpperCase());
        }
        m.appendTail(sb);
        // Capitalize very first character
        if (!sb.isEmpty()) {
            sb.setCharAt(0, Character.toUpperCase(sb.charAt(0)));
        }
        return sb.toString();
    }

    public static String toCamelCase(String text) {
        String[] words = text.split("[\\s_\\-]+");
        if (words.length == 0) return text;
        StringBuilder sb = new StringBuilder(words[0].toLowerCase());
        for (int i = 1; i < words.length; i++) {
            if (!words[i].isEmpty()) {
                sb.append(Character.toUpperCase(words[i].charAt(0)));
                sb.append(words[i].substring(1).toLowerCase());
            }
        }
        return sb.toString();
    }

    public static String toPascalCase(String text) {
        String camel = toCamelCase(text);
        if (camel.isEmpty()) return camel;
        return Character.toUpperCase(camel.charAt(0)) + camel.substring(1);
    }

    public static String toSnakeCase(String text) {
        return text.trim()
                   .replaceAll("([A-Z])", "_$1")
                   .replaceAll("[\\s\\-]+", "_")
                   .replaceAll("_+", "_")
                   .toLowerCase()
                   .replaceAll("^_", "");
    }

    public static String toKebabCase(String text) {
        return toSnakeCase(text).replace('_', '-');
    }

    // ── Trim Operations ───────────────────────────────────────────────────────

    public static String trimLeading(String text) {
        return Arrays.stream(text.split("\n", -1))
                     .map(l -> l.replaceAll("^\\s+", ""))
                     .collect(Collectors.joining("\n"));
    }

    public static String trimTrailing(String text) {
        return Arrays.stream(text.split("\n", -1))
                     .map(l -> l.replaceAll("\\s+$", ""))
                     .collect(Collectors.joining("\n"));
    }

    public static String trimAll(String text) {
        return trimTrailing(trimLeading(text));
    }

    public static String collapseSpaces(String text) {
        return text.replaceAll("[ \\t]+", " ");
    }

    // ── Line Operations ───────────────────────────────────────────────────────

    public static String removeEmptyLines(String text) {
        return Arrays.stream(text.split("\n", -1))
                     .filter(l -> !l.trim().isEmpty())
                     .collect(Collectors.joining("\n"));
    }

    public static String removeDuplicateLines(String text) {
        return Arrays.stream(text.split("\n", -1))
                     .distinct()
                     .collect(Collectors.joining("\n"));
    }

    public static String sortLinesAZ(String text) {
        return Arrays.stream(text.split("\n", -1))
                     .sorted(String.CASE_INSENSITIVE_ORDER)
                     .collect(Collectors.joining("\n"));
    }

    public static String sortLinesZA(String text) {
        return Arrays.stream(text.split("\n", -1))
                     .sorted(Comparator.reverseOrder())
                     .collect(Collectors.joining("\n"));
    }

    public static String sortLinesNumericAsc(String text) {
        return Arrays.stream(text.split("\n", -1))
                     .sorted(Comparator.comparingDouble(l -> {
                         try { return Double.parseDouble(l.trim()); }
                         catch (NumberFormatException e) { return 0.0; }
                     }))
                     .collect(Collectors.joining("\n"));
    }

    public static String sortLinesNumericDesc(String text) {
        return Arrays.stream(text.split("\n", -1))
                     .sorted(Comparator.<String, Double>comparing(l -> {
                         try { return Double.parseDouble(l.trim()); }
                         catch (NumberFormatException e) { return 0.0; }
                     }).reversed())
                     .collect(Collectors.joining("\n"));
    }

    public static String joinLines(String text) {
        return Arrays.stream(text.split("\n", -1))
                     .map(String::trim)
                     .filter(l -> !l.isEmpty())
                     .collect(Collectors.joining(" "));
    }

    public static String splitOnDelimiter(String text, String delimiter) {
        return Arrays.stream(text.split(Pattern.quote(delimiter), -1))
                     .map(String::trim)
                     .collect(Collectors.joining("\n"));
    }

    public static String reverseLines(String text) {
        String[] lines = text.split("\n", -1);
        List<String> list = Arrays.asList(lines);
        Collections.reverse(list);
        return String.join("\n", list);
    }

    public static String shuffleLines(String text) {
        List<String> lines = new ArrayList<>(Arrays.asList(text.split("\n", -1)));
        Collections.shuffle(lines);
        return String.join("\n", lines);
    }

    // ── Statistics ────────────────────────────────────────────────────────────

    public static int countChars(String text) {
        return text.length();
    }

    public static int countCharsNoSpaces(String text) {
        return text.replaceAll("\\s", "").length();
    }

    public static int countWords(String text) {
        if (text == null || text.isBlank()) return 0;
        return text.trim().split("\\s+").length;
    }

    public static int countLines(String text) {
        if (text.isEmpty()) return 0;
        return text.split("\n", -1).length;
    }

    public static int countParagraphs(String text) {
        if (text == null || text.isBlank()) return 0;
        return text.trim().split("\n{2,}").length;
    }

    /** Estimated reading time in minutes (avg 200 WPM). */
    public static double readingTimeMinutes(String text) {
        return (double) countWords(text) / 200.0;
    }

    public static String readingTimeFormatted(String text) {
        double mins = readingTimeMinutes(text);
        if (mins < 1) return "< 1 min read";
        return String.format("%.0f min read", Math.ceil(mins));
    }

    // ── Date / Time Insertion ─────────────────────────────────────────────────

    public static String getDate(String format) {
        return LocalDateTime.now().format(DateTimeFormatter.ofPattern(format));
    }

    public static String getDateTime_DDMMYYYY()    { return getDate("dd/MM/yyyy"); }
    public static String getDateTime_MMDDYYYY()    { return getDate("MM/dd/yyyy"); }
    public static String getDateTime_ISO()         { return getDate("yyyy-MM-dd"); }
    public static String getDateTime_Time24()      { return getDate("HH:mm:ss"); }
    public static String getDateTime_Time12()      { return getDate("hh:mm:ss a"); }
    public static String getDateTime_Full()        { return getDate("yyyy-MM-dd HH:mm:ss"); }
    public static String getDateTime_Timestamp()   { return getDate("yyyy-MM-dd'T'HH:mm:ss"); }

    // ── Search Utilities ──────────────────────────────────────────────────────

    /** Counts how many times a substring appears (non-overlapping). */
    public static int countOccurrences(String text, String query, boolean caseSensitive) {
        if (query == null || query.isEmpty()) return 0;
        String t = caseSensitive ? text : text.toLowerCase();
        String q = caseSensitive ? query : query.toLowerCase();
        int count = 0, idx = 0;
        while ((idx = t.indexOf(q, idx)) != -1) { count++; idx += q.length(); }
        return count;
    }

    /** Wraps text at a given column width. */
    public static String wrapText(String text, int width) {
        StringBuilder sb = new StringBuilder();
        for (String line : text.split("\n", -1)) {
            int start = 0;
            while (start < line.length()) {
                int end = Math.min(start + width, line.length());
                if (end < line.length() && line.charAt(end) != ' ') {
                    int space = line.lastIndexOf(' ', end);
                    if (space > start) end = space;
                }
                sb.append(line, start, end).append("\n");
                start = end;
                if (start < line.length() && line.charAt(start) == ' ') start++;
            }
        }
        return sb.toString().stripTrailing();
    }

    // ── Prefix / Suffix ───────────────────────────────────────────────────────

    public static String addPrefix(String text, String prefix) {
        return Arrays.stream(text.split("\n", -1))
                     .map(l -> prefix + l)
                     .collect(Collectors.joining("\n"));
    }

    public static String addSuffix(String text, String suffix) {
        return Arrays.stream(text.split("\n", -1))
                     .map(l -> l + suffix)
                     .collect(Collectors.joining("\n"));
    }

    // ── Encode / Decode ───────────────────────────────────────────────────────

    public static String encodeBase64(String text) {
        return Base64.getEncoder().encodeToString(text.getBytes());
    }

    public static String decodeBase64(String text) {
        try { return new String(Base64.getDecoder().decode(text.trim())); }
        catch (Exception e) { return "Invalid Base64 input"; }
    }

    // ── Reverse text ─────────────────────────────────────────────────────────

    public static String reverseText(String text) {
        return new StringBuilder(text).reverse().toString();
    }

    public static String reverseWords(String text) {
        String[] words = text.split("\\s+");
        StringBuilder sb = new StringBuilder();
        for (int i = words.length - 1; i >= 0; i--) {
            if (!sb.isEmpty()) sb.append(" ");
            sb.append(words[i]);
        }
        return sb.toString();
    }
}
