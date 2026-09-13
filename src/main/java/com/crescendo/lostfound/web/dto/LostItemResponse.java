package com.crescendo.lostfound.web.dto;

import com.crescendo.lostfound.domain.LostItem;

import java.time.Instant;

public record LostItemResponse(
        Long id,
        String itemName,
        String place,
        int quantity,
        int claimedQuantity,
        int remainingQuantity,
        Instant reportedAt
) {
    public static LostItemResponse from(LostItem item) {
        return new LostItemResponse(
                item.getId(),
                item.getItemName(),
                item.getPlace(),
                item.getQuantity(),
                item.getClaimedQuantity(),
                item.getRemainingQuantity(),
                item.getReportedAt());
    }
}
