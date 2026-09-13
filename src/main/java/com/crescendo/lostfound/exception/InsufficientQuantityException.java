package com.crescendo.lostfound.exception;

/**
 * Thrown when a claim requests more units of a lost item than are currently
 * available. Deliberately reports the exact remaining quantity so the client
 * can decide whether to retry with a smaller amount.
 */
public class InsufficientQuantityException extends RuntimeException {

    public InsufficientQuantityException(Long itemId, int requestedQuantity, int availableQuantity) {
        super("Cannot claim %d unit(s) of lost item %d: only %d available"
                .formatted(requestedQuantity, itemId, availableQuantity));
    }
}
