package com.crescendo.lostfound.web;

import com.crescendo.lostfound.domain.ItemClaim;
import com.crescendo.lostfound.service.ClaimService;
import com.crescendo.lostfound.web.dto.ClaimRequest;
import com.crescendo.lostfound.web.dto.ClaimResponse;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/**
 * Lets a user claim a quantity of a lost item. Modeled as a sub-resource of the
 * lost item ({@code POST /lost-items/{itemId}/claims}) rather than a single
 * "claim multiple items" call: each claim is a single atomic business
 * transaction (see {@link com.crescendo.lostfound.repository.LostItemRepository#tryClaimQuantity}),
 * and a client claiming several items just issues one call per item - simpler
 * to reason about and to retry than a partially-successful batch endpoint.
 */
@RestController
@RequestMapping("/api/v1/lost-items/{itemId}/claims")
@PreAuthorize("hasRole('USER')")
public class ClaimController {

    private final ClaimService claimService;

    public ClaimController(ClaimService claimService) {
        this.claimService = claimService;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public ClaimResponse claim(@PathVariable Long itemId, @Valid @RequestBody ClaimRequest request,
                                Authentication authentication) {
        String userId = authentication.getName();
        ItemClaim claim = claimService.claim(itemId, userId, request.quantity());
        return ClaimResponse.from(claim);
    }
}
