package com.crescendo.lostfound.exception;

/** Thrown when no registered {@code LostItemFileParser} supports the uploaded file. */
public class UnsupportedFileTypeException extends RuntimeException {

    public UnsupportedFileTypeException(String filename, String contentType) {
        super("Unsupported file type for '%s' (content-type: %s). Only PDF is currently supported."
                .formatted(filename, contentType));
    }
}
