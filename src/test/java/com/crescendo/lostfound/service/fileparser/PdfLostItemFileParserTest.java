package com.crescendo.lostfound.service.fileparser;

import com.crescendo.lostfound.exception.FileParsingException;
import com.crescendo.lostfound.support.TestPdfBuilder;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.InputStream;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class PdfLostItemFileParserTest {

    private final PdfLostItemFileParser parser = new PdfLostItemFileParser();

    @Test
    void supportsPdfByContentTypeOrExtension() {
        assertThat(parser.supports("anything.txt", "application/pdf")).isTrue();
        assertThat(parser.supports("items.PDF", "application/octet-stream")).isTrue();
        assertThat(parser.supports("items.csv", "text/csv")).isFalse();
    }

    @Test
    void parsesTheSampleFileFromTheAssignment() {
        // Two "Laptop" records at different places must both survive as distinct items.
        List<ParsedLostItem> items = parser.parseText("""
                ItemName: Laptop
                Quantity: 1
                Place: Taxi

                ItemName: Headphones
                Quantity: 2
                Place: Railway station

                ItemName: Jewels
                Quantity: 4
                Place: Airport

                ItemName: Laptop
                Quantity: 1
                Place: Airport
                """);

        assertThat(items).containsExactly(
                new ParsedLostItem("Laptop", 1, "Taxi"),
                new ParsedLostItem("Headphones", 2, "Railway station"),
                new ParsedLostItem("Jewels", 4, "Airport"),
                new ParsedLostItem("Laptop", 1, "Airport"));
    }

    @Test
    void parsesRecordsWithNoBlankLineBetweenThem() {
        // Some PDF generators express inter-record spacing via layout/leading rather than an
        // actual blank text line, so PDFTextStripper extracts no blank line at all here. Every
        // record must still come out distinct, keyed off the ItemName boundary rather than a
        // blank-line separator.
        List<ParsedLostItem> items = parser.parseText("""
                ItemName: Laptop
                Quantity: 1
                Place: Taxi
                ItemName: Headphones
                Quantity: 2
                Place: Railway station
                ItemName: Jewels
                Quantity: 4
                Place: Airport
                ItemName: Laptop
                Quantity: 1
                Place: Airport
                """);

        assertThat(items).containsExactly(
                new ParsedLostItem("Laptop", 1, "Taxi"),
                new ParsedLostItem("Headphones", 2, "Railway station"),
                new ParsedLostItem("Jewels", 4, "Airport"),
                new ParsedLostItem("Laptop", 1, "Airport"));
    }

    @Test
    void isCaseInsensitiveAndToleratesExtraWhitespace() {
        List<ParsedLostItem> items = parser.parseText("""
                itemname:   Wallet
                QUANTITY:1
                place :  Bus stop
                """);

        assertThat(items).containsExactly(new ParsedLostItem("Wallet", 1, "Bus stop"));
    }

    @Test
    void returnsEmptyListForBlankInput() {
        assertThat(parser.parseText("")).isEmpty();
        assertThat(parser.parseText("   \n\n  \n")).isEmpty();
    }

    @Test
    void ignoresUnrelatedLinesWithinARecord() {
        List<ParsedLostItem> items = parser.parseText("""
                Lost & Found Report - September
                ItemName: Umbrella
                Quantity: 3
                Place: Bus Station
                """);

        assertThat(items).containsExactly(new ParsedLostItem("Umbrella", 3, "Bus Station"));
    }

    @Test
    void rejectsRecordMissingAField() {
        assertThatThrownBy(() -> parser.parseText("""
                ItemName: Laptop
                Place: Taxi
                """))
                .isInstanceOf(FileParsingException.class)
                .hasMessageContaining("Incomplete lost item record");
    }

    @Test
    void rejectsNonNumericQuantity() {
        assertThatThrownBy(() -> parser.parseText("""
                ItemName: Laptop
                Quantity: two
                Place: Taxi
                """))
                .isInstanceOf(FileParsingException.class)
                .hasMessageContaining("Invalid quantity value 'two'");
    }

    @Test
    void rejectsNonPositiveQuantity() {
        assertThatThrownBy(() -> parser.parseText("""
                ItemName: Laptop
                Quantity: 0
                Place: Taxi
                """))
                .isInstanceOf(FileParsingException.class)
                .hasMessageContaining("quantity must be positive");
    }

    @Test
    void extractsRecordsFromAnActualPdfDocument() throws IOException {
        try (InputStream pdf = TestPdfBuilder.buildPdf(List.of(
                "ItemName: Laptop",
                "Quantity: 1",
                "Place: Taxi",
                "",
                "ItemName: Headphones",
                "Quantity: 2",
                "Place: Railway station"))) {

            List<ParsedLostItem> items = parser.parse(pdf);

            assertThat(items).containsExactly(
                    new ParsedLostItem("Laptop", 1, "Taxi"),
                    new ParsedLostItem("Headphones", 2, "Railway station"));
        }
    }
}
