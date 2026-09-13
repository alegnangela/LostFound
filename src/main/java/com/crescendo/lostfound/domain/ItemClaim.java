package com.crescendo.lostfound.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.Objects;

/**
 * Records that a given user claimed a quantity of a {@link LostItem}.
 *
 * <p>A single lost item can have many claims from different users (partial
 * quantities), so this is a many-to-one relationship, not a one-to-one.
 * Creating a claim is always paired with a successful call to
 * {@link com.crescendo.lostfound.repository.LostItemRepository#tryClaimQuantity}
 * in the same transaction so the two never drift apart.
 */
@Entity
@Table(name = "item_claim")
public class ItemClaim {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "lost_item_id", nullable = false)
    private LostItem lostItem;

    @Column(name = "user_id", nullable = false)
    private String userId;

    @Column(nullable = false)
    private int quantity;

    @Column(name = "claimed_at", nullable = false, updatable = false)
    private Instant claimedAt;

    protected ItemClaim() {
        // required by JPA
    }

    public ItemClaim(LostItem lostItem, String userId, int quantity) {
        this.lostItem = Objects.requireNonNull(lostItem, "lostItem must not be null");
        if (userId == null || userId.isBlank()) {
            throw new IllegalArgumentException("userId must not be blank");
        }
        if (quantity <= 0) {
            throw new IllegalArgumentException("quantity must be positive, was " + quantity);
        }
        this.userId = userId.trim();
        this.quantity = quantity;
        this.claimedAt = Instant.now();
    }

    public Long getId() {
        return id;
    }

    public LostItem getLostItem() {
        return lostItem;
    }

    public String getUserId() {
        return userId;
    }

    public int getQuantity() {
        return quantity;
    }

    public Instant getClaimedAt() {
        return claimedAt;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof ItemClaim other)) return false;
        return id != null && id.equals(other.id);
    }

    @Override
    public int hashCode() {
        return Objects.hashCode(id);
    }
}
