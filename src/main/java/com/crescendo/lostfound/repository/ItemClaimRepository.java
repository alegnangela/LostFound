package com.crescendo.lostfound.repository;

import com.crescendo.lostfound.domain.ItemClaim;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Collection;
import java.util.List;

public interface ItemClaimRepository extends JpaRepository<ItemClaim, Long> {

    List<ItemClaim> findByLostItemIdIn(Collection<Long> lostItemIds);
}
