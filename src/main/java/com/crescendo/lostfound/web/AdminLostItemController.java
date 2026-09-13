package com.crescendo.lostfound.web;

import com.crescendo.lostfound.domain.ItemClaim;
import com.crescendo.lostfound.domain.LostItem;
import com.crescendo.lostfound.service.ClaimService;
import com.crescendo.lostfound.service.LostItemService;
import com.crescendo.lostfound.service.userdirectory.UserDirectoryClient;
import com.crescendo.lostfound.web.dto.ClaimantResponse;
import com.crescendo.lostfound.web.dto.ImportResponse;
import com.crescendo.lostfound.web.dto.LostItemResponse;
import com.crescendo.lostfound.web.dto.LostItemWithClaimsResponse;
import com.crescendo.lostfound.web.dto.PageResponse;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.PositiveOrZero;
import org.springframework.data.domain.Page;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/** Admin-only endpoints. */
@RestController
@RequestMapping("/api/v1/admin/lost-items")
@PreAuthorize("hasRole('ADMIN')")
public class AdminLostItemController {

    private final LostItemService lostItemService;
    private final ClaimService claimService;
    private final UserDirectoryClient userDirectoryClient;

    public AdminLostItemController(LostItemService lostItemService,
                                    ClaimService claimService,
                                    UserDirectoryClient userDirectoryClient) {
        this.lostItemService = lostItemService;
        this.claimService = claimService;
        this.userDirectoryClient = userDirectoryClient;
    }

    @PostMapping("/import")
    @ResponseStatus(HttpStatus.CREATED)
    public ImportResponse importItems(@RequestParam("file") MultipartFile file) {
        List<LostItem> imported = lostItemService.importFromFile(file);
        List<LostItemResponse> response = imported.stream().map(LostItemResponse::from).toList();
        return new ImportResponse(response.size(), response);
    }

    /**
     * One page of lost items together with who has claimed them (userId, resolved name, quantity).
     * The page is taken over items, and claims are loaded only for the items on it, so the cost of
     * a request is bounded by the page size rather than by the total number of claims.
     */
    @GetMapping("/claims")
    public PageResponse<LostItemWithClaimsResponse> getItemsWithClaims(
            @RequestParam(defaultValue = Paging.DEFAULT_PAGE) @PositiveOrZero int page,
            @RequestParam(defaultValue = Paging.DEFAULT_SIZE) @Min(1) @Max(Paging.MAX_SIZE) int size) {
        Page<LostItem> items = lostItemService.findAll(page, size);
        List<Long> itemIds = items.getContent().stream().map(LostItem::getId).toList();
        List<ItemClaim> claims = claimService.findByLostItemIds(itemIds);

        Map<Long, List<ItemClaim>> claimsByItemId = claims.stream()
                .collect(Collectors.groupingBy(claim -> claim.getLostItem().getId()));

        // Resolve each distinct user's name once, even if they claimed multiple items.
        Map<String, String> resolvedNames = new HashMap<>();

        return PageResponse.from(items,
                item -> LostItemWithClaimsResponse.from(item, toClaimants(claimsByItemId, item, resolvedNames)));
    }

    private List<ClaimantResponse> toClaimants(Map<Long, List<ItemClaim>> claimsByItemId,
                                                LostItem item,
                                                Map<String, String> resolvedNames) {
        return claimsByItemId.getOrDefault(item.getId(), List.of()).stream()
                .map(claim -> new ClaimantResponse(
                        claim.getUserId(),
                        resolvedNames.computeIfAbsent(claim.getUserId(), userDirectoryClient::getUserName),
                        claim.getQuantity()))
                .toList();
    }
}
