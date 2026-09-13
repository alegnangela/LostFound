package com.crescendo.lostfound.service;

import com.crescendo.lostfound.domain.LostItem;
import com.crescendo.lostfound.exception.InsufficientQuantityException;
import com.crescendo.lostfound.repository.LostItemRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Proves that {@link LostItemRepository#tryClaimQuantity} prevents the lost-update
 * race that a naive "read remaining, check, then write" approach would be prone to:
 * many threads race to over-claim a scarce item, and the total ever actually
 * claimed must never exceed what was available.
 */
@SpringBootTest
@AutoConfigureTestDatabase
@ActiveProfiles("test")
class ClaimServiceConcurrencyTest {

    @Autowired
    private ClaimService claimService;
    @Autowired
    private LostItemRepository lostItemRepository;

    @Test
    void concurrentClaimsNeverOversellALostItem() throws InterruptedException {
        LostItem item = lostItemRepository.save(new LostItem("Jewels", 5, "Airport"));

        int attempts = 10;
        int quantityPerAttempt = 1;
        ExecutorService pool = Executors.newFixedThreadPool(attempts);
        try {
            List<Callable<Boolean>> tasks = java.util.stream.IntStream.range(0, attempts)
                    .<Callable<Boolean>>mapToObj(i -> () -> {
                        try {
                            claimService.claim(item.getId(), "user-" + i, quantityPerAttempt);
                            return true;
                        } catch (InsufficientQuantityException e) {
                            return false;
                        }
                    })
                    .toList();

            List<Future<Boolean>> results = pool.invokeAll(tasks);
            AtomicInteger successes = new AtomicInteger();
            results.forEach(future -> {
                try {
                    if (future.get()) {
                        successes.incrementAndGet();
                    }
                } catch (Exception e) {
                    throw new RuntimeException(e);
                }
            });

            assertThat(successes.get()).isEqualTo(5); // only 5 units were ever available
            LostItem finalState = lostItemRepository.findById(item.getId()).orElseThrow();
            assertThat(finalState.getClaimedQuantity()).isEqualTo(5);
            assertThat(finalState.getRemainingQuantity()).isZero();
        } finally {
            pool.shutdown();
            pool.awaitTermination(10, TimeUnit.SECONDS);
        }
    }
}
