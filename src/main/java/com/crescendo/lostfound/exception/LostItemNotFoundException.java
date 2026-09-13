package com.crescendo.lostfound.exception;

public class LostItemNotFoundException extends RuntimeException {

    public LostItemNotFoundException(Long itemId) {
        super("Lost item not found: " + itemId);
    }
}
