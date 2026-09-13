package com.crescendo.lostfound.web.dto;

import com.crescendo.lostfound.domain.LostItem;

import java.util.List;

public record LostItemWithClaimsResponse(
        Long id,
        String itemName,
        String place,
        int quantity,
        int claimedQuantity,
        int remainingQuantity,
        List<ClaimantResponse> claimants
) {
    public static LostItemWithClaimsResponse from(LostItem item, List<ClaimantResponse> claimants) {
        return new LostItemWithClaimsResponse(
                item.getId(),
                item.getItemName(),
                item.getPlace(),
                item.getQuantity(),
                item.getClaimedQuantity(),
                item.getRemainingQuantity(),
                claimants);
    }
}
