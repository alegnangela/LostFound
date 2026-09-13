package com.crescendo.lostfound.domain;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class LostItemTest {

    @Test
    void remainingQuantityEqualsQuantityWhenNothingClaimedYet() {
        LostItem item = new LostItem("Laptop", 3, "Airport");

        assertThat(item.getQuantity()).isEqualTo(3);
        assertThat(item.getClaimedQuantity()).isZero();
        assertThat(item.getRemainingQuantity()).isEqualTo(3);
    }

    @Test
    void rejectsBlankItemName() {
        assertThatThrownBy(() -> new LostItem("  ", 1, "Airport"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("itemName");
    }

    @Test
    void rejectsBlankPlace() {
        assertThatThrownBy(() -> new LostItem("Laptop", 1, " "))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("place");
    }

    @Test
    void rejectsNonPositiveQuantity() {
        assertThatThrownBy(() -> new LostItem("Laptop", 0, "Airport"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("quantity");

        assertThatThrownBy(() -> new LostItem("Laptop", -1, "Airport"))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
