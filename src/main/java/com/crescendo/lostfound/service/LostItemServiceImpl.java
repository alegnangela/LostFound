package com.crescendo.lostfound.service;

import com.crescendo.lostfound.domain.LostItem;
import com.crescendo.lostfound.exception.FileParsingException;
import com.crescendo.lostfound.exception.LostItemNotFoundException;
import com.crescendo.lostfound.repository.LostItemRepository;
import com.crescendo.lostfound.service.fileparser.LostItemFileParser;
import com.crescendo.lostfound.service.fileparser.LostItemFileParserResolver;
import com.crescendo.lostfound.service.fileparser.ParsedLostItem;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.InputStream;
import java.util.List;

@Service
public class LostItemServiceImpl implements LostItemService {

    private static final Logger log = LoggerFactory.getLogger(LostItemServiceImpl.class);
    private static final Sort STABLE_ORDER = Sort.by("id");

    private final LostItemRepository lostItemRepository;
    private final LostItemFileParserResolver parserResolver;

    public LostItemServiceImpl(LostItemRepository lostItemRepository, LostItemFileParserResolver parserResolver) {
        this.lostItemRepository = lostItemRepository;
        this.parserResolver = parserResolver;
    }

    @Override
    @Transactional
    public List<LostItem> importFromFile(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new FileParsingException("Uploaded file is empty");
        }

        LostItemFileParser parser = parserResolver.resolve(file.getOriginalFilename(), file.getContentType());
        log.debug("Parsing '{}' ({} bytes, {}) with {}", file.getOriginalFilename(), file.getSize(),
                file.getContentType(), parser.getClass().getSimpleName());
        List<ParsedLostItem> parsedItems = readAndParse(file, parser);

        if (parsedItems.isEmpty()) {
            throw new FileParsingException("No lost item records found in '" + file.getOriginalFilename() + "'");
        }

        List<LostItem> items = parsedItems.stream()
                .map(parsed -> new LostItem(parsed.itemName(), parsed.quantity(), parsed.place()))
                .toList();
        List<LostItem> saved = lostItemRepository.saveAll(items);
        log.info("Imported {} lost item(s) from '{}'", saved.size(), file.getOriginalFilename());
        return saved;
    }

    private List<ParsedLostItem> readAndParse(MultipartFile file, LostItemFileParser parser) {
        try (InputStream in = file.getInputStream()) {
            return parser.parse(in);
        } catch (IOException e) {
            throw new FileParsingException("Failed to read uploaded file '" + file.getOriginalFilename() + "'", e);
        }
    }

    @Override
    @Transactional(readOnly = true)
    public Page<LostItem> findAll(int page, int size) {
        return lostItemRepository.findAll(PageRequest.of(page, size, STABLE_ORDER));
    }

    @Override
    @Transactional(readOnly = true)
    public LostItem getById(Long id) {
        return lostItemRepository.findById(id).orElseThrow(() -> new LostItemNotFoundException(id));
    }
}
