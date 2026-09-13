package com.crescendo.lostfound.service.fileparser;

import com.crescendo.lostfound.exception.UnsupportedFileTypeException;
import org.springframework.stereotype.Component;

import java.util.List;

/** Picks the {@link LostItemFileParser} that supports a given upload out of all registered parsers. */
@Component
public class LostItemFileParserResolver {

    private final List<LostItemFileParser> parsers;

    public LostItemFileParserResolver(List<LostItemFileParser> parsers) {
        this.parsers = List.copyOf(parsers);
    }

    public LostItemFileParser resolve(String filename, String contentType) {
        return parsers.stream()
                .filter(parser -> parser.supports(filename, contentType))
                .findFirst()
                .orElseThrow(() -> new UnsupportedFileTypeException(filename, contentType));
    }
}
