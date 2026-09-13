package com.crescendo.lostfound.web.dto;

import java.util.List;

public record ImportResponse(int itemsImported, List<LostItemResponse> items) {
}
