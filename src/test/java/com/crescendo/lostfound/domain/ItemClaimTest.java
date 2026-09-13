package com.crescendo.lostfound.domain;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ItemClaimTest {

    private final LostItem lostItem = new LostItem("Laptop", 3, "Airport");

    @Test
    void storesTheClaimingUserAndQuantity() {
        ItemClaim claim = new ItemClaim(lostItem, "1001", 2);

        assertThat(claim.getUserId()).isEqualTo("1001");
        assertThat(claim.getQuantity()).isEqualTo(2);
        assertThat(claim.getLostItem()).isSameAs(lostItem);
        assertThat(claim.getClaimedAt()).isNotNull();
    }

    @Test
    void rejectsBlankUserId() {
        assertThatThrownBy(() -> new ItemClaim(lostItem, " ", 1))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("userId");
    }

    @Test
    void rejectsNonPositiveQuantity() {
        assertThatThrownBy(() -> new ItemClaim(lostItem, "1001", 0))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("quantity");
    }

    @Test
    void rejectsNullLostItem() {
        assertThatThrownBy(() -> new ItemClaim(null, "1001", 1))
                .isInstanceOf(NullPointerException.class);
    }
}
