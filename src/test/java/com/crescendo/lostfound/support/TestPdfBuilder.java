package com.crescendo.lostfound.support;

import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.font.PDType1Font;
import org.apache.pdfbox.pdmodel.font.Standard14Fonts;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.List;

/**
 * Builds a real, in-memory PDF from plain text lines, so tests can exercise the
 * actual PDF-extraction code path (via PDFBox) instead of only unit-testing the
 * text parsing logic against a hand-written string. Blank entries in
 * {@code lines} render as an empty line, which is what {@link
 * com.crescendo.lostfound.service.fileparser.PdfLostItemFileParser} uses to
 * separate records.
 */
public final class TestPdfBuilder {

    private TestPdfBuilder() {
    }

    public static InputStream buildPdf(List<String> lines) {
        try (PDDocument document = new PDDocument()) {
            PDPage page = new PDPage();
            document.addPage(page);

            try (PDPageContentStream content = new PDPageContentStream(document, page)) {
                content.beginText();
                content.setFont(new PDType1Font(Standard14Fonts.FontName.HELVETICA), 12);
                content.setLeading(14.5f);
                content.newLineAtOffset(50, 700);
                for (String line : lines) {
                    // PDFTextStripper only emits a line in its output for a line that actually
                    // rendered a glyph; a truly empty showText produces no text position at all,
                    // so the "blank" separator line must render a rendered-but-invisible-when-
                    // trimmed space to force a corresponding blank line in the extracted text.
                    content.showText(line.isEmpty() ? " " : line);
                    content.newLine();
                }
                content.endText();
            }

            ByteArrayOutputStream out = new ByteArrayOutputStream();
            document.save(out);
            return new ByteArrayInputStream(out.toByteArray());
        } catch (IOException e) {
            throw new IllegalStateException("Failed to build test PDF", e);
        }
    }
}
