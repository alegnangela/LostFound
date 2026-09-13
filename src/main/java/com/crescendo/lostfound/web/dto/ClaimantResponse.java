package com.crescendo.lostfound.web.dto;

/** One user's claim against a lost item, with the display name resolved from the User Service. */
public record ClaimantResponse(String userId, String userName, int quantity) {
}
