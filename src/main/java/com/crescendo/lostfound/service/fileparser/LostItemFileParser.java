package com.crescendo.lostfound.service.fileparser;

import java.io.IOException;
import java.io.InputStream;
import java.util.List;

/**
 * Strategy for turning an uploaded file into {@link ParsedLostItem} records.
 *
 * <p>The assignment asks for PDF specifically, but admins will inevitably ask
 * "can we also upload a CSV/plain-text export" - this interface exists so that
 * adding a format is a matter of adding one more {@code @Component} bean, not
 * touching the service or controller layer ({@link LostItemFileParserResolver}
 * picks the matching one).
 */
public interface LostItemFileParser {

    /** Whether this parser can handle a file with the given name and/or content type. */
    boolean supports(String filename, String contentType);

    /**
     * Parses the file content into lost item records.
     *
     * @throws com.crescendo.lostfound.exception.FileParsingException if the content does not match the expected format
     */
    List<ParsedLostItem> parse(InputStream input) throws IOException;
}
