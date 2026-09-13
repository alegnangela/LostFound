package com.crescendo.lostfound.repository;

import com.crescendo.lostfound.domain.LostItem;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;

import static org.assertj.core.api.Assertions.assertThat;

@DataJpaTest
class LostItemRepositoryTest {

    @Autowired
    private LostItemRepository repository;

    @Test
    void claimingWithinAvailableQuantitySucceedsAndUpdatesTheRow() {
        LostItem item = repository.save(new LostItem("Laptop", 5, "Airport"));

        int updatedRows = repository.tryClaimQuantity(item.getId(), 3);

        assertThat(updatedRows).isEqualTo(1);
        LostItem reloaded = repository.findById(item.getId()).orElseThrow();
        assertThat(reloaded.getClaimedQuantity()).isEqualTo(3);
        assertThat(reloaded.getRemainingQuantity()).isEqualTo(2);
    }

    @Test
    void claimingExactlyTheRemainingQuantitySucceeds() {
        LostItem item = repository.save(new LostItem("Laptop", 5, "Airport"));

        int updatedRows = repository.tryClaimQuantity(item.getId(), 5);

        assertThat(updatedRows).isEqualTo(1);
        assertThat(repository.findById(item.getId()).orElseThrow().getRemainingQuantity()).isZero();
    }

    @Test
    void claimingMoreThanRemainingQuantityAffectsNoRows() {
        LostItem item = repository.save(new LostItem("Laptop", 5, "Airport"));
        repository.tryClaimQuantity(item.getId(), 4); // 1 left

        int updatedRows = repository.tryClaimQuantity(item.getId(), 2);

        assertThat(updatedRows).isZero();
        assertThat(repository.findById(item.getId()).orElseThrow().getRemainingQuantity()).isEqualTo(1);
    }

    @Test
    void claimingAgainstANonExistentItemAffectsNoRows() {
        assertThat(repository.tryClaimQuantity(999_999L, 1)).isZero();
    }
}
