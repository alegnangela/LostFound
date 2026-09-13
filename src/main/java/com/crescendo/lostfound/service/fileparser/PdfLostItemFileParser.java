package com.crescendo.lostfound.service.fileparser;

import com.crescendo.lostfound.exception.FileParsingException;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.text.PDFTextStripper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Parses a PDF laid out as blank-line-separated records of {@code ItemName:},
 * {@code Quantity:} and {@code Place:} fields, e.g.:
 *
 * <pre>
 * ItemName: Laptop
 * Quantity: 1
 * Place: Taxi
 *
 * ItemName: Headphones
 * Quantity: 2
 * Place: Railway station
 * </pre>
 *
 * <p>Field matching is case-insensitive and tolerant of extra whitespace, since
 * the exact casing/spacing produced by whatever tool generated the PDF is not
 * something we control. Any record missing a field, or with a non-numeric
 * quantity, fails the whole upload (see {@link FileParsingException}) rather
 * than being silently skipped or partially imported - an admin re-uploading a
 * corrected file is far less confusing than a partially-imported one.
 */
@Component
public class PdfLostItemFileParser implements LostItemFileParser {

    private static final Logger log = LoggerFactory.getLogger(PdfLostItemFileParser.class);
    private static final Pattern FIELD_LINE = Pattern.compile("(?i)^(ItemName|Quantity|Place)\\s*:\\s*(.*)$");
    private static final Pattern LINE_SEPARATOR = Pattern.compile("\\r?\\n");

    @Override
    public boolean supports(String filename, String contentType) {
        boolean pdfContentType = "application/pdf".equalsIgnoreCase(contentType);
        boolean pdfExtension = filename != null && filename.toLowerCase(Locale.ROOT).endsWith(".pdf");
        return pdfContentType || pdfExtension;
    }

    @Override
    public List<ParsedLostItem> parse(InputStream input) throws IOException {
        String text;
        try (PDDocument document = Loader.loadPDF(input.readAllBytes())) {
            text = new PDFTextStripper().getText(document);
            log.debug("Extracted {} characters of text from {} PDF page(s)", text.length(), document.getNumberOfPages());
        }
        List<ParsedLostItem> items = parseText(text);
        log.debug("Parsed {} lost item record(s)", items.size());
        return items;
    }

    /**
     * Package-private to allow direct unit testing of the text format without going through PDF
     * rendering.
     *
     * <p>Records are delimited by an {@code ItemName:} line starting a new one, not by blank
     * lines: PDF text extraction reproduces inter-record spacing inconsistently across PDF
     * generators - a gap achieved via paragraph leading rather than an actual blank text line
     * yields no blank line at all in the extracted text, which would otherwise merge every
     * record's fields into one (each field silently overwriting the previous record's value,
     * with only the last record's data surviving and no error to signal it). Every record does,
     * by contract, carry exactly one {@code ItemName} field, so it is the reliable boundary.
     */
    List<ParsedLostItem> parseText(String text) {
        List<ParsedLostItem> items = new ArrayList<>();
        List<String> currentBlock = new ArrayList<>();
        for (String line : LINE_SEPARATOR.split(text)) {
            if (line.isBlank()) {
                continue;
            }
            String trimmedLine = line.trim();
            if (isItemNameLine(trimmedLine) && !currentBlock.isEmpty()) {
                addItemIfPresent(items, currentBlock);
                currentBlock = new ArrayList<>();
            }
            currentBlock.add(trimmedLine);
        }
        addItemIfPresent(items, currentBlock);
        return items;
    }

    private boolean isItemNameLine(String line) {
        Matcher matcher = FIELD_LINE.matcher(line);
        return matcher.matches() && "ItemName".equalsIgnoreCase(matcher.group(1));
    }

    private void addItemIfPresent(List<ParsedLostItem> items, List<String> blockLines) {
        Map<String, String> fields = extractFields(blockLines);
        if (fields.isEmpty()) {
            return;
        }
        items.add(toParsedItem(fields, String.join("\n", blockLines)));
    }

    private Map<String, String> extractFields(List<String> blockLines) {
        Map<String, String> fields = new LinkedHashMap<>();
        for (String line : blockLines) {
            Matcher matcher = FIELD_LINE.matcher(line);
            if (matcher.matches()) {
                fields.put(matcher.group(1).toLowerCase(Locale.ROOT), matcher.group(2).trim());
            }
        }
        return fields;
    }

    private ParsedLostItem toParsedItem(Map<String, String> fields, String rawBlock) {
        String itemName = fields.get("itemname");
        String quantityRaw = fields.get("quantity");
        String place = fields.get("place");

        if (isBlank(itemName) || quantityRaw == null || isBlank(place)) {
            throw new FileParsingException(
                    "Incomplete lost item record, expected ItemName/Quantity/Place: [" + rawBlock.trim() + "]");
        }

        int quantity;
        try {
            quantity = Integer.parseInt(quantityRaw.trim());
        } catch (NumberFormatException e) {
            throw new FileParsingException(
                    "Invalid quantity value '" + quantityRaw + "' in record: [" + rawBlock.trim() + "]");
        }

        try {
            return new ParsedLostItem(itemName, quantity, place);
        } catch (IllegalArgumentException e) {
            throw new FileParsingException(e.getMessage() + " in record: [" + rawBlock.trim() + "]");
        }
    }

    private static boolean isBlank(String value) {
        return value == null || value.isBlank();
    }
}
