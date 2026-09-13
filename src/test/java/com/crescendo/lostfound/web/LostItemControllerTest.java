package com.crescendo.lostfound.web;

import com.crescendo.lostfound.config.SecurityConfig;
import com.crescendo.lostfound.domain.LostItem;
import com.crescendo.lostfound.exception.LostItemNotFoundException;
import com.crescendo.lostfound.service.LostItemService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;

import static org.hamcrest.Matchers.containsString;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(LostItemController.class)
@Import(SecurityConfig.class)
@ActiveProfiles("test")
class LostItemControllerTest {

    @Autowired
    private MockMvc mockMvc;
    @MockitoBean
    private LostItemService lostItemService;

    @Test
    void anonymousRequestsAreRejected() throws Exception {
        mockMvc.perform(get("/api/v1/lost-items"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @WithMockUser(roles = "USER")
    void authenticatedUserCanListLostItemsWithDefaultPaging() throws Exception {
        when(lostItemService.findAll(0, 20)).thenReturn(page(List.of(new LostItem("Laptop", 1, "Taxi")), 0, 20, 1));

        mockMvc.perform(get("/api/v1/lost-items"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].itemName").value("Laptop"))
                .andExpect(jsonPath("$.content[0].remainingQuantity").value(1))
                .andExpect(jsonPath("$.page").value(0))
                .andExpect(jsonPath("$.size").value(20))
                .andExpect(jsonPath("$.totalElements").value(1))
                .andExpect(jsonPath("$.totalPages").value(1));
    }

    @Test
    @WithMockUser(roles = "USER")
    void passesRequestedPageAndSizeToTheService() throws Exception {
        when(lostItemService.findAll(2, 5)).thenReturn(page(List.of(), 2, 5, 11));

        mockMvc.perform(get("/api/v1/lost-items").param("page", "2").param("size", "5"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.page").value(2))
                .andExpect(jsonPath("$.totalElements").value(11))
                .andExpect(jsonPath("$.totalPages").value(3));
        verify(lostItemService).findAll(2, 5);
    }

    @Test
    @WithMockUser(roles = "USER")
    void rejectsPageSizeAboveTheMaximum() throws Exception {
        mockMvc.perform(get("/api/v1/lost-items").param("size", "101"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value(containsString("size")));
        verifyNoInteractions(lostItemService);
    }

    @Test
    @WithMockUser(roles = "USER")
    void rejectsZeroPageSize() throws Exception {
        mockMvc.perform(get("/api/v1/lost-items").param("size", "0"))
                .andExpect(status().isBadRequest());
        verifyNoInteractions(lostItemService);
    }

    @Test
    @WithMockUser(roles = "USER")
    void rejectsNegativePage() throws Exception {
        mockMvc.perform(get("/api/v1/lost-items").param("page", "-1"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value(containsString("page")));
        verifyNoInteractions(lostItemService);
    }

    @Test
    @WithMockUser(roles = "USER")
    void rejectsNonNumericPage() throws Exception {
        mockMvc.perform(get("/api/v1/lost-items").param("page", "abc"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("page: invalid value 'abc'"));
        verifyNoInteractions(lostItemService);
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    void adminCanAlsoReadTheUserFacingListEndpoint() throws Exception {
        when(lostItemService.findAll(anyInt(), anyInt())).thenReturn(page(List.of(), 0, 20, 0));

        mockMvc.perform(get("/api/v1/lost-items")).andExpect(status().isOk());
    }

    @Test
    @WithMockUser(roles = "USER")
    void returnsNotFoundForAnUnknownId() throws Exception {
        when(lostItemService.getById(404L)).thenThrow(new LostItemNotFoundException(404L));

        mockMvc.perform(get("/api/v1/lost-items/404"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.message").value("Lost item not found: 404"));
    }

    private static Page<LostItem> page(List<LostItem> content, int page, int size, long total) {
        return new PageImpl<>(content, PageRequest.of(page, size), total);
    }
}
