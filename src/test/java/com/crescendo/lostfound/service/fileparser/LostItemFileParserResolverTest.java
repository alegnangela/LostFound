package com.crescendo.lostfound.service.fileparser;

import com.crescendo.lostfound.exception.UnsupportedFileTypeException;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.InputStream;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class LostItemFileParserResolverTest {

    private final LostItemFileParser pdfParser = fixedSupportParser(true);
    private final LostItemFileParser resolver1 = fixedSupportParser(false);

    @Test
    void resolvesToTheFirstParserThatSupportsTheFile() {
        LostItemFileParserResolver resolver = new LostItemFileParserResolver(List.of(resolver1, pdfParser));

        assertThat(resolver.resolve("report.pdf", "application/pdf")).isSameAs(pdfParser);
    }

    @Test
    void throwsWhenNoParserSupportsTheFile() {
        LostItemFileParserResolver resolver = new LostItemFileParserResolver(List.of(resolver1));

        assertThatThrownBy(() -> resolver.resolve("report.csv", "text/csv"))
                .isInstanceOf(UnsupportedFileTypeException.class)
                .hasMessageContaining("report.csv");
    }

    @Test
    void throwsWhenNoParsersAreRegisteredAtAll() {
        LostItemFileParserResolver resolver = new LostItemFileParserResolver(List.of());

        assertThatThrownBy(() -> resolver.resolve("report.pdf", "application/pdf"))
                .isInstanceOf(UnsupportedFileTypeException.class);
    }

    private static LostItemFileParser fixedSupportParser(boolean supports) {
        return new LostItemFileParser() {
            @Override
            public boolean supports(String filename, String contentType) {
                return supports;
            }

            @Override
            public List<ParsedLostItem> parse(InputStream input) throws IOException {
                throw new UnsupportedOperationException("not needed for this test");
            }
        };
    }
}
