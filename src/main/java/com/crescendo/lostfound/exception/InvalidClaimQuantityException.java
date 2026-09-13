package com.crescendo.lostfound.exception;

public class InvalidClaimQuantityException extends RuntimeException {

    public InvalidClaimQuantityException(int quantity) {
        super("Claim quantity must be positive, was " + quantity);
    }
}
