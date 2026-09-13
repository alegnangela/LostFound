package com.crescendo.lostfound.web;

import com.crescendo.lostfound.config.SecurityConfig;
import com.crescendo.lostfound.domain.ItemClaim;
import com.crescendo.lostfound.domain.LostItem;
import com.crescendo.lostfound.service.ClaimService;
import com.crescendo.lostfound.service.LostItemService;
import com.crescendo.lostfound.service.userdirectory.UserDirectoryClient;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(AdminLostItemController.class)
@Import(SecurityConfig.class)
@ActiveProfiles("test")
class AdminLostItemControllerTest {

    @Autowired
    private MockMvc mockMvc;
    @MockitoBean
    private LostItemService lostItemService;
    @MockitoBean
    private ClaimService claimService;
    @MockitoBean
    private UserDirectoryClient userDirectoryClient;

    @Test
    @WithMockUser(roles = "ADMIN")
    void adminCanImportAFile() throws Exception {
        MockMultipartFile file = new MockMultipartFile("file", "items.pdf", "application/pdf", "irrelevant".getBytes());
        when(lostItemService.importFromFile(any())).thenReturn(List.of(new LostItem("Laptop", 1, "Taxi")));

        mockMvc.perform(multipart("/api/v1/admin/lost-items/import").file(file))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.itemsImported").value(1))
                .andExpect(jsonPath("$.items[0].itemName").value("Laptop"));
    }

    @Test
    @WithMockUser(roles = "USER")
    void regularUserCannotImportAFile() throws Exception {
        MockMultipartFile file = new MockMultipartFile("file", "items.pdf", "application/pdf", "irrelevant".getBytes());

        mockMvc.perform(multipart("/api/v1/admin/lost-items/import").file(file))
                .andExpect(status().isForbidden());
    }

    @Test
    void anonymousCannotImportAFile() throws Exception {
        MockMultipartFile file = new MockMultipartFile("file", "items.pdf", "application/pdf", "irrelevant".getBytes());

        mockMvc.perform(multipart("/api/v1/admin/lost-items/import").file(file))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    void adminSeesClaimantNamesResolvedFromTheUserDirectory() throws Exception {
        LostItem laptop = com.crescendo.lostfound.support.TestEntityIds.assignId(new LostItem("Laptop", 2, "Taxi"), 1L);
        ItemClaim claim = new ItemClaim(laptop, "1001", 1);
        when(lostItemService.findAll(0, 20)).thenReturn(new PageImpl<>(List.of(laptop), PageRequest.of(0, 20), 1));
        when(claimService.findByLostItemIds(any())).thenReturn(List.of(claim));
        when(userDirectoryClient.getUserName("1001")).thenReturn("Alice");

        mockMvc.perform(get("/api/v1/admin/lost-items/claims"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].claimants[0].userId").value("1001"))
                .andExpect(jsonPath("$.content[0].claimants[0].userName").value("Alice"))
                .andExpect(jsonPath("$.content[0].claimants[0].quantity").value(1))
                .andExpect(jsonPath("$.totalElements").value(1));
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    void claimsReportLoadsClaimsOnlyForItemsOnTheRequestedPage() throws Exception {
        LostItem wallet = com.crescendo.lostfound.support.TestEntityIds.assignId(new LostItem("Wallet", 1, "Bus"), 2L);
        when(lostItemService.findAll(1, 1)).thenReturn(new PageImpl<>(List.of(wallet), PageRequest.of(1, 1), 3));
        when(claimService.findByLostItemIds(List.of(2L))).thenReturn(List.of());

        mockMvc.perform(get("/api/v1/admin/lost-items/claims").param("page", "1").param("size", "1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].itemName").value("Wallet"))
                .andExpect(jsonPath("$.content[0].claimants").isEmpty())
                .andExpect(jsonPath("$.page").value(1))
                .andExpect(jsonPath("$.totalPages").value(3));
        verify(claimService).findByLostItemIds(List.of(2L));
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    void claimsReportRejectsPageSizeAboveTheMaximum() throws Exception {
        mockMvc.perform(get("/api/v1/admin/lost-items/claims").param("size", "101"))
                .andExpect(status().isBadRequest());
        verifyNoInteractions(lostItemService, claimService);
    }

    @Test
    @WithMockUser(roles = "USER")
    void regularUserCannotReadTheClaimsReport() throws Exception {
        mockMvc.perform(get("/api/v1/admin/lost-items/claims"))
                .andExpect(status().isForbidden());
    }
}
