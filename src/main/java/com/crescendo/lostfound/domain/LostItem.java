package com.crescendo.lostfound.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.Objects;

/**
 * A single lost item record extracted from an admin-uploaded file, e.g.:
 * {@code ItemName: Laptop, Quantity: 1, Place: Taxi}.
 *
 * <p>Two entries for the same item name found in different places (or reported
 * separately) are distinct records - the file is a log of finds, not a catalog
 * keyed by item name.
 *
 * <p>{@code claimedQuantity} is only ever mutated through
 * {@link com.crescendo.lostfound.repository.LostItemRepository#tryClaimQuantity},
 * a single atomic conditional UPDATE. That keeps this class effectively
 * immutable from the application's point of view and avoids lost-update races
 * under concurrent claims - see that method's Javadoc for why.
 */
@Entity
@Table(name = "lost_item")
public class LostItem {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "item_name", nullable = false)
    private String itemName;

    @Column(nullable = false)
    private String place;

    @Column(nullable = false)
    private int quantity;

    @Column(name = "claimed_quantity", nullable = false)
    private int claimedQuantity;

    @Column(name = "reported_at", nullable = false, updatable = false)
    private Instant reportedAt;

    protected LostItem() {
        // required by JPA
    }

    public LostItem(String itemName, int quantity, String place) {
        this.itemName = requireNonBlank(itemName, "itemName");
        this.place = requireNonBlank(place, "place");
        if (quantity <= 0) {
            throw new IllegalArgumentException("quantity must be positive, was " + quantity);
        }
        this.quantity = quantity;
        this.claimedQuantity = 0;
        this.reportedAt = Instant.now();
    }

    private static String requireNonBlank(String value, String field) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(field + " must not be blank");
        }
        return value.trim();
    }

    public Long getId() {
        return id;
    }

    public String getItemName() {
        return itemName;
    }

    public String getPlace() {
        return place;
    }

    public int getQuantity() {
        return quantity;
    }

    public int getClaimedQuantity() {
        return claimedQuantity;
    }

    /** Quantity still available to be claimed. Never negative by construction. */
    public int getRemainingQuantity() {
        return quantity - claimedQuantity;
    }

    public Instant getReportedAt() {
        return reportedAt;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof LostItem other)) return false;
        return id != null && id.equals(other.id);
    }

    @Override
    public int hashCode() {
        return Objects.hashCode(id);
    }
}
