package com.crescendo.lostfound.service;

import com.crescendo.lostfound.domain.ItemClaim;
import com.crescendo.lostfound.domain.LostItem;
import com.crescendo.lostfound.exception.InsufficientQuantityException;
import com.crescendo.lostfound.exception.InvalidClaimQuantityException;
import com.crescendo.lostfound.exception.LostItemNotFoundException;
import com.crescendo.lostfound.repository.ItemClaimRepository;
import com.crescendo.lostfound.repository.LostItemRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ClaimServiceImplTest {

    @Mock
    private LostItemRepository lostItemRepository;
    @Mock
    private ItemClaimRepository itemClaimRepository;

    private ClaimServiceImpl service;

    @BeforeEach
    void setUp() {
        service = new ClaimServiceImpl(lostItemRepository, itemClaimRepository);
    }

    @Test
    void claimSucceedsWhenEnoughQuantityRemains() {
        LostItem item = new LostItem("Laptop", 3, "Airport");
        when(lostItemRepository.tryClaimQuantity(7L, 2)).thenReturn(1);
        when(lostItemRepository.getReferenceById(7L)).thenReturn(item);
        when(itemClaimRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

        ItemClaim claim = service.claim(7L, "1001", 2);

        assertThat(claim.getUserId()).isEqualTo("1001");
        assertThat(claim.getQuantity()).isEqualTo(2);
        assertThat(claim.getLostItem()).isSameAs(item);
    }

    @Test
    void throwsInsufficientQuantityWhenAtomicUpdateAffectsNoRows() {
        LostItem item = new LostItem("Laptop", 3, "Airport"); // only 3 remaining
        when(lostItemRepository.tryClaimQuantity(7L, 5)).thenReturn(0);
        when(lostItemRepository.findById(7L)).thenReturn(Optional.of(item));

        assertThatThrownBy(() -> service.claim(7L, "1001", 5))
                .isInstanceOf(InsufficientQuantityException.class)
                .hasMessageContaining("5")
                .hasMessageContaining("3"); // reports the actually-remaining quantity
        verify(itemClaimRepository, never()).save(any());
    }

    @Test
    void throwsNotFoundWhenItemDoesNotExist() {
        when(lostItemRepository.tryClaimQuantity(99L, 1)).thenReturn(0);
        when(lostItemRepository.findById(99L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.claim(99L, "1001", 1))
                .isInstanceOf(LostItemNotFoundException.class);
        verify(itemClaimRepository, never()).save(any());
    }

    @Test
    void rejectsZeroOrNegativeQuantityBeforeTouchingTheRepository() {
        assertThatThrownBy(() -> service.claim(7L, "1001", 0))
                .isInstanceOf(InvalidClaimQuantityException.class);
        assertThatThrownBy(() -> service.claim(7L, "1001", -1))
                .isInstanceOf(InvalidClaimQuantityException.class);

        verifyNoInteractions(lostItemRepository, itemClaimRepository);
    }

    @Test
    void findByLostItemIdsReturnsEmptyListWithoutQueryingWhenNoIdsGiven() {
        assertThat(service.findByLostItemIds(List.of())).isEmpty();
        verifyNoInteractions(itemClaimRepository);
    }

    @Test
    void findByLostItemIdsDelegatesToRepositoryWhenIdsGiven() {
        LostItem item = new LostItem("Laptop", 3, "Airport");
        ItemClaim claim = new ItemClaim(item, "1001", 1);
        when(itemClaimRepository.findByLostItemIdIn(List.of(7L))).thenReturn(List.of(claim));

        assertThat(service.findByLostItemIds(List.of(7L))).containsExactly(claim);
    }
}
