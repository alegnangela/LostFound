package com.crescendo.lostfound.web.dto;

import jakarta.validation.constraints.Positive;

public record ClaimRequest(
        @Positive(message = "quantity must be greater than zero") int quantity
) {
}
