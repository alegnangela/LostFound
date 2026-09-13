package com.crescendo.lostfound.service;

import com.crescendo.lostfound.domain.ItemClaim;
import com.crescendo.lostfound.domain.LostItem;
import com.crescendo.lostfound.exception.InsufficientQuantityException;
import com.crescendo.lostfound.exception.InvalidClaimQuantityException;
import com.crescendo.lostfound.exception.LostItemNotFoundException;
import com.crescendo.lostfound.repository.ItemClaimRepository;
import com.crescendo.lostfound.repository.LostItemRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Collection;
import java.util.List;

@Service
public class ClaimServiceImpl implements ClaimService {

    private static final Logger log = LoggerFactory.getLogger(ClaimServiceImpl.class);

    private final LostItemRepository lostItemRepository;
    private final ItemClaimRepository itemClaimRepository;

    public ClaimServiceImpl(LostItemRepository lostItemRepository, ItemClaimRepository itemClaimRepository) {
        this.lostItemRepository = lostItemRepository;
        this.itemClaimRepository = itemClaimRepository;
    }

    @Override
    @Transactional
    public ItemClaim claim(Long lostItemId, String userId, int quantity) {
        if (quantity <= 0) {
            throw new InvalidClaimQuantityException(quantity);
        }

        int updatedRows = lostItemRepository.tryClaimQuantity(lostItemId, quantity);
        if (updatedRows == 0) {
            LostItem item = lostItemRepository.findById(lostItemId)
                    .orElseThrow(() -> new LostItemNotFoundException(lostItemId));
            throw new InsufficientQuantityException(lostItemId, quantity, item.getRemainingQuantity());
        }

        // Persistence context was cleared by tryClaimQuantity; getReferenceById gives a fresh,
        // lazily-loaded proxy rather than re-fetching a full row we don't otherwise need.
        LostItem claimedItem = lostItemRepository.getReferenceById(lostItemId);
        ItemClaim claim = itemClaimRepository.save(new ItemClaim(claimedItem, userId, quantity));
        log.info("User {} claimed {} unit(s) of lost item {}", userId, quantity, lostItemId);
        return claim;
    }

    @Override
    @Transactional(readOnly = true)
    public List<ItemClaim> findByLostItemIds(Collection<Long> lostItemIds) {
        if (lostItemIds.isEmpty()) {
            return List.of();
        }
        return itemClaimRepository.findByLostItemIdIn(lostItemIds);
    }
}
