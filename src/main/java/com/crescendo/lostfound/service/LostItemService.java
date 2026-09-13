package com.crescendo.lostfound.service;

import com.crescendo.lostfound.domain.LostItem;
import org.springframework.data.domain.Page;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

public interface LostItemService {

    /**
     * Parses an admin-uploaded file and persists every lost item record found in it.
     *
     * @throws com.crescendo.lostfound.exception.FileParsingException      if the file is empty, unreadable, or malformed
     * @throws com.crescendo.lostfound.exception.UnsupportedFileTypeException if no parser supports the file's type
     */
    List<LostItem> importFromFile(MultipartFile file);

    /**
     * One page of lost items, always ordered by id: without a deterministic order the database may
     * return rows in a different order per query, so pages could overlap or skip items. Newly
     * imported items get higher ids and are appended at the end, leaving earlier pages stable.
     *
     * @param page zero-based page index
     */
    Page<LostItem> findAll(int page, int size);

    /** @throws com.crescendo.lostfound.exception.LostItemNotFoundException if no item with that id exists */
    LostItem getById(Long id);
}
