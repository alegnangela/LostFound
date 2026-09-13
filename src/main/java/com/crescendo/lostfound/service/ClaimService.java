package com.crescendo.lostfound.service;

import com.crescendo.lostfound.domain.ItemClaim;

import java.util.Collection;
import java.util.List;

public interface ClaimService {

    /**
     * Claims {@code quantity} units of the given lost item on behalf of {@code userId}.
     *
     * @throws com.crescendo.lostfound.exception.InvalidClaimQuantityException if quantity is not positive
     * @throws com.crescendo.lostfound.exception.LostItemNotFoundException     if no item with that id exists
     * @throws com.crescendo.lostfound.exception.InsufficientQuantityException if fewer than {@code quantity} units remain
     */
    ItemClaim claim(Long lostItemId, String userId, int quantity);

    /** All claims for the given lost items, e.g. for building an admin claims report. */
    List<ItemClaim> findByLostItemIds(Collection<Long> lostItemIds);
}
