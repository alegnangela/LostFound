package com.crescendo.lostfound.web.dto;

import com.crescendo.lostfound.domain.ItemClaim;

import java.time.Instant;

public record ClaimResponse(
        Long claimId,
        Long lostItemId,
        String userId,
        int quantity,
        Instant claimedAt
) {
    public static ClaimResponse from(ItemClaim claim) {
        return new ClaimResponse(
                claim.getId(),
                claim.getLostItem().getId(),
                claim.getUserId(),
                claim.getQuantity(),
                claim.getClaimedAt());
    }
}
