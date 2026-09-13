package com.crescendo.lostfound.web;

import com.crescendo.lostfound.service.LostItemService;
import com.crescendo.lostfound.web.dto.LostItemResponse;
import com.crescendo.lostfound.web.dto.PageResponse;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.PositiveOrZero;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** Read-only endpoints available to any authenticated user (and admins). */
@RestController
@RequestMapping("/api/v1/lost-items")
@PreAuthorize("hasAnyRole('ADMIN', 'USER')")
public class LostItemController {

    private final LostItemService lostItemService;

    public LostItemController(LostItemService lostItemService) {
        this.lostItemService = lostItemService;
    }

    @GetMapping
    public PageResponse<LostItemResponse> getAll(
            @RequestParam(defaultValue = Paging.DEFAULT_PAGE) @PositiveOrZero int page,
            @RequestParam(defaultValue = Paging.DEFAULT_SIZE) @Min(1) @Max(Paging.MAX_SIZE) int size) {
        return PageResponse.from(lostItemService.findAll(page, size), LostItemResponse::from);
    }

    @GetMapping("/{id}")
    public LostItemResponse getOne(@PathVariable Long id) {
        return LostItemResponse.from(lostItemService.getById(id));
    }
}
