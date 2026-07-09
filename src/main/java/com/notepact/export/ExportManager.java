package com.notepact.export;

import com.notepact.model.DocumentModel;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.common.PDRectangle;
import org.apache.pdfbox.pdmodel.font.PDType1Font;
import org.apache.pdfbox.pdmodel.font.Standard14Fonts;

import java.io.*;
import java.nio.file.*;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Handles export to TXT, HTML, Markdown, and PDF formats.
 */
public class ExportManager {

    private static final Logger LOG = Logger.getLogger(ExportManager.class.getName());

    // ── TXT ───────────────────────────────────────────────────────────────────

    public static void exportTxt(File dest, String content) throws IOException {
        Files.writeString(dest.toPath(), content);
    }

    // ── HTML ──────────────────────────────────────────────────────────────────

    public static void exportHtml(File dest, String content, DocumentModel doc) throws IOException {
        String escaped = content
                .replace("&", "&amp;")
                .replace("<", "&lt;")
                .replace(">", "&gt;")
                .replace("\"", "&quot;");

        String html = """
                <!DOCTYPE html>
                <html lang="en">
                <head>
                  <meta charset="UTF-8">
                  <meta name="viewport" content="width=device-width, initial-scale=1.0">
                  <title>%s</title>
                  <style>
                    body { font-family: 'Courier New', monospace; background: #1e1e1e;
                           color: #d4d4d4; margin: 40px; line-height: 1.6; }
                    pre  { white-space: pre-wrap; word-wrap: break-word; }
                    .header { color: #858585; font-size: 12px; margin-bottom: 20px;
                              padding-bottom: 10px; border-bottom: 1px solid #333; }
                  </style>
                </head>
                <body>
                  <div class="header">
                    %s &nbsp;·&nbsp; Exported: %s &nbsp;·&nbsp; %d characters
                  </div>
                  <pre>%s</pre>
                </body>
                </html>
                """.formatted(
                doc.getTitle(),
                doc.getTitle(),
                LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm")),
                content.length(),
                escaped);

        Files.writeString(dest.toPath(), html);
    }

    // ── Markdown ──────────────────────────────────────────────────────────────

    public static void exportMarkdown(File dest, String content) throws IOException {
        Files.writeString(dest.toPath(), content);
    }

    // ── PDF ───────────────────────────────────────────────────────────────────

    public static void exportPdf(File dest, String content, DocumentModel doc) throws IOException {
        try (PDDocument pdf = new PDDocument()) {
            PDType1Font font      = new PDType1Font(Standard14Fonts.FontName.COURIER);
            PDType1Font fontBold  = new PDType1Font(Standard14Fonts.FontName.COURIER_BOLD);

            float fontSize    = 10f;
            float leading     = 14f;
            float margin      = 50f;
            PDRectangle page  = PDRectangle.A4;
            float width       = page.getWidth()  - 2 * margin;
            float height      = page.getHeight() - 2 * margin;
            float topY        = page.getHeight() - margin;

            String[] lines = content.split("\n", -1);

            PDPage currentPage = new PDPage(page);
            pdf.addPage(currentPage);
            PDPageContentStream stream = new PDPageContentStream(pdf, currentPage);

            // Header
            stream.beginText();
            stream.setFont(fontBold, fontSize);
            stream.newLineAtOffset(margin, topY);
            stream.showText(doc.getTitle() + " — Notepact");
            stream.endText();

            // Separator line
            stream.setLineWidth(0.5f);
            stream.moveTo(margin, topY - 5);
            stream.lineTo(page.getWidth() - margin, topY - 5);
            stream.stroke();

            // Content
            stream.setFont(font, fontSize);
            float y = topY - 20;

            for (String rawLine : lines) {
                // Break long lines
                java.util.List<String> wrapped = wrapLine(rawLine, font, fontSize, width);
                for (String wl : wrapped) {
                    if (y < margin + leading) {
                        stream.close();
                        currentPage = new PDPage(page);
                        pdf.addPage(currentPage);
                        stream = new PDPageContentStream(pdf, currentPage);
                        stream.setFont(font, fontSize);
                        y = topY - 20;
                    }
                    stream.beginText();
                    stream.setFont(font, fontSize);
                    stream.newLineAtOffset(margin, y);
                    stream.showText(sanitize(wl));
                    stream.endText();
                    y -= leading;
                }
            }

            stream.close();

            // Footer — page numbers
            int pageNum = 1;
            for (PDPage p : pdf.getPages()) {
                try (PDPageContentStream footer = new PDPageContentStream(
                        pdf, p, PDPageContentStream.AppendMode.APPEND, true)) {
                    footer.beginText();
                    footer.setFont(font, 8f);
                    footer.newLineAtOffset(page.getWidth() / 2 - 10, margin / 2);
                    footer.showText("Page " + pageNum++);
                    footer.endText();
                }
            }

            pdf.save(dest);
            LOG.info("PDF exported: " + dest.getAbsolutePath());
        }
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private static java.util.List<String> wrapLine(String line, PDType1Font font, float fontSize, float maxWidth) throws IOException {
        java.util.List<String> result = new java.util.ArrayList<>();
        if (line.isEmpty()) { result.add(""); return result; }

        StringBuilder current = new StringBuilder();
        for (int i = 0; i < line.length(); i++) {
            current.append(line.charAt(i));
            float w = font.getStringWidth(current.toString()) / 1000f * fontSize;
            if (w > maxWidth) {
                if (current.length() > 1) {
                    result.add(current.substring(0, current.length() - 1));
                    current = new StringBuilder(String.valueOf(line.charAt(i)));
                } else {
                    result.add(current.toString());
                    current = new StringBuilder();
                }
            }
        }
        if (!current.isEmpty()) result.add(current.toString());
        return result;
    }

    private static String sanitize(String text) {
        // Remove non-Latin-1 characters for PDFBox basic font compatibility
        return text.chars()
                   .filter(c -> c < 256)
                   .collect(StringBuilder::new, (sb, c) -> sb.append((char) c), StringBuilder::append)
                   .toString();
    }

    // ── helper alias ────────────────────────────────────────────────────────
}
