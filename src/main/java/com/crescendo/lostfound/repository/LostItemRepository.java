package com.crescendo.lostfound.repository;

import com.crescendo.lostfound.domain.LostItem;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface LostItemRepository extends JpaRepository<LostItem, Long> {

    /**
     * Atomically claims {@code quantity} units of the given lost item, in a single
     * conditional UPDATE, and returns the number of rows affected (0 or 1).
     *
     * <p>The WHERE clause re-checks availability against the row's committed value,
     * so the database's row-level locking - not application code - is what makes this
     * safe under concurrent claims: if two requests race for the last unit, the
     * second one's UPDATE blocks until the first commits, then re-evaluates the
     * condition and affects zero rows. The service layer treats a 0 result as
     * "not enough remaining" (or "item does not exist").
     *
     * <p>This was chosen over optimistic locking (a {@code @Version} field with a
     * catch-and-retry loop) because it needs no retry logic, costs a single
     * round trip, and is a well-worn pattern for inventory-style decrements.
     * {@code clearAutomatically = true} evicts the now-stale entity from the
     * persistence context so a subsequent read reflects the DB truth rather than
     * an in-memory copy.
     */
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("""
            update LostItem li
               set li.claimedQuantity = li.claimedQuantity + :quantity
             where li.id = :id
               and (li.quantity - li.claimedQuantity) >= :quantity
            """)
    int tryClaimQuantity(@Param("id") Long id, @Param("quantity") int quantity);
}
