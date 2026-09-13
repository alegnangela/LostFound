package com.crescendo.lostfound.service;

import com.crescendo.lostfound.domain.LostItem;
import com.crescendo.lostfound.exception.FileParsingException;
import com.crescendo.lostfound.exception.LostItemNotFoundException;
import com.crescendo.lostfound.repository.LostItemRepository;
import com.crescendo.lostfound.service.fileparser.LostItemFileParser;
import com.crescendo.lostfound.service.fileparser.LostItemFileParserResolver;
import com.crescendo.lostfound.service.fileparser.ParsedLostItem;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class LostItemServiceImplTest {

    @Mock
    private LostItemRepository lostItemRepository;
    @Mock
    private LostItemFileParserResolver parserResolver;
    @Mock
    private LostItemFileParser parser;

    private LostItemServiceImpl service;

    @BeforeEach
    void setUp() {
        service = new LostItemServiceImpl(lostItemRepository, parserResolver);
    }

    @Test
    void importsAndPersistsEveryParsedRecord() throws IOException {
        MultipartFile file = new MockMultipartFile("file", "items.pdf", "application/pdf", "irrelevant".getBytes());
        when(parserResolver.resolve("items.pdf", "application/pdf")).thenReturn(parser);
        when(parser.parse(any())).thenReturn(List.of(
                new ParsedLostItem("Laptop", 1, "Taxi"),
                new ParsedLostItem("Headphones", 2, "Railway station")));
        when(lostItemRepository.saveAll(any())).thenAnswer(invocation -> invocation.getArgument(0));

        List<LostItem> result = service.importFromFile(file);

        assertThat(result).hasSize(2);
        assertThat(result.get(0).getItemName()).isEqualTo("Laptop");
        assertThat(result.get(1).getItemName()).isEqualTo("Headphones");
    }

    @Test
    void rejectsNullFile() {
        assertThatThrownBy(() -> service.importFromFile(null))
                .isInstanceOf(FileParsingException.class);
        verifyNoInteractions(parserResolver, lostItemRepository);
    }

    @Test
    void rejectsEmptyFile() {
        MultipartFile emptyFile = new MockMultipartFile("file", "items.pdf", "application/pdf", new byte[0]);

        assertThatThrownBy(() -> service.importFromFile(emptyFile))
                .isInstanceOf(FileParsingException.class)
                .hasMessageContaining("empty");
        verifyNoInteractions(parserResolver, lostItemRepository);
    }

    @Test
    void rejectsFileThatParsesToNoRecords() throws IOException {
        MultipartFile file = new MockMultipartFile("file", "empty.pdf", "application/pdf", "irrelevant".getBytes());
        when(parserResolver.resolve(anyString(), anyString())).thenReturn(parser);
        when(parser.parse(any())).thenReturn(List.of());

        assertThatThrownBy(() -> service.importFromFile(file))
                .isInstanceOf(FileParsingException.class)
                .hasMessageContaining("No lost item records");
        verify(lostItemRepository, org.mockito.Mockito.never()).saveAll(any());
    }

    @Test
    void findAllRequestsTheGivenPageOrderedById() {
        LostItem laptop = new LostItem("Laptop", 1, "Taxi");
        PageRequest expectedRequest = PageRequest.of(1, 5, Sort.by("id"));
        when(lostItemRepository.findAll(expectedRequest)).thenReturn(new PageImpl<>(List.of(laptop), expectedRequest, 6));

        Page<LostItem> result = service.findAll(1, 5);

        assertThat(result.getContent()).containsExactly(laptop);
        assertThat(result.getTotalElements()).isEqualTo(6);
        verify(lostItemRepository).findAll(expectedRequest);
    }

    @Test
    void getByIdThrowsWhenNotFound() {
        when(lostItemRepository.findById(42L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.getById(42L))
                .isInstanceOf(LostItemNotFoundException.class)
                .hasMessageContaining("42");
    }
}
