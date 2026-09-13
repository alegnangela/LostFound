package com.crescendo.lostfound.exception;

/** Thrown when an uploaded file cannot be read or its content does not match the expected format. */
public class FileParsingException extends RuntimeException {

    public FileParsingException(String message) {
        super(message);
    }

    public FileParsingException(String message, Throwable cause) {
        super(message, cause);
    }
}
