package com.crescendo.lostfound.service.fileparser;

import java.util.Objects;

/** One lost item record extracted from an uploaded file, before it becomes a persisted {@code LostItem}. */
public record ParsedLostItem(String itemName, int quantity, String place) {

    public ParsedLostItem {
        Objects.requireNonNull(itemName, "itemName");
        Objects.requireNonNull(place, "place");
        if (itemName.isBlank()) {
            throw new IllegalArgumentException("itemName must not be blank");
        }
        if (place.isBlank()) {
            throw new IllegalArgumentException("place must not be blank");
        }
        if (quantity <= 0) {
            throw new IllegalArgumentException("quantity must be positive, was " + quantity);
        }
    }
}
