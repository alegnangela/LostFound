package com.crescendo.lostfound.web;

import com.crescendo.lostfound.config.SecurityConfig;
import com.crescendo.lostfound.domain.ItemClaim;
import com.crescendo.lostfound.domain.LostItem;
import com.crescendo.lostfound.exception.InsufficientQuantityException;
import com.crescendo.lostfound.exception.LostItemNotFoundException;
import com.crescendo.lostfound.service.ClaimService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(ClaimController.class)
@Import(SecurityConfig.class)
@ActiveProfiles("test")
class ClaimControllerTest {

    @Autowired
    private MockMvc mockMvc;
    @MockitoBean
    private ClaimService claimService;

    @Test
    @WithMockUser(username = "alice", roles = "USER")
    void aRegularUserClaimsAsTheirOwnAuthenticatedIdentity() throws Exception {
        LostItem item = new LostItem("Laptop", 3, "Airport");
        when(claimService.claim(7L, "alice", 2)).thenReturn(new ItemClaim(item, "alice", 2));

        mockMvc.perform(post("/api/v1/lost-items/7/claims")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"quantity": 2}
                                """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.userId").value("alice"))
                .andExpect(jsonPath("$.quantity").value(2));
    }

    @Test
    @WithMockUser(username = "alice", roles = "USER")
    void aRegularUserCannotClaimOnBehalfOfSomeoneElseByNamingThemInTheBody() throws Exception {
        LostItem item = new LostItem("Laptop", 3, "Airport");
        when(claimService.claim(7L, "alice", 2)).thenReturn(new ItemClaim(item, "alice", 2));

        // "mallory" in the body must be ignored - the claim is still recorded as "alice".
        mockMvc.perform(post("/api/v1/lost-items/7/claims")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"userId": "mallory", "quantity": 2}
                                """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.userId").value("alice"));

        verify(claimService, never()).claim(anyLong(), eq("mallory"), anyInt());
    }

    @Test
    @WithMockUser(username = "root", roles = "ADMIN")
    void anAdminOnlyAccountCannotClaim() throws Exception {
        mockMvc.perform(post("/api/v1/lost-items/7/claims")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"quantity": 2}
                                """))
                .andExpect(status().isForbidden());

        verifyNoInteractions(claimService);
    }

    @Test
    @WithMockUser(username = "carol", roles = {"ADMIN", "USER"})
    void someoneWithBothRolesClaimsAsThemselves() throws Exception {
        LostItem item = new LostItem("Laptop", 3, "Airport");
        when(claimService.claim(7L, "carol", 1)).thenReturn(new ItemClaim(item, "carol", 1));

        mockMvc.perform(post("/api/v1/lost-items/7/claims")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"userId": "1001", "quantity": 1}
                                """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.userId").value("carol"));
    }

    @Test
    @WithMockUser(roles = "USER")
    void nonPositiveQuantityIsRejectedWithBadRequest() throws Exception {
        mockMvc.perform(post("/api/v1/lost-items/7/claims")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"quantity": 0}
                                """))
                .andExpect(status().isBadRequest());
    }

    @Test
    @WithMockUser(roles = "USER")
    void insufficientQuantityIsReportedAsConflict() throws Exception {
        when(claimService.claim(anyLong(), anyString(), anyInt()))
                .thenThrow(new InsufficientQuantityException(7L, 10, 3));

        mockMvc.perform(post("/api/v1/lost-items/7/claims")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"quantity": 10}
                                """))
                .andExpect(status().isConflict());
    }

    @Test
    @WithMockUser(roles = "USER")
    void claimingAnUnknownItemReturnsNotFound() throws Exception {
        when(claimService.claim(anyLong(), anyString(), anyInt()))
                .thenThrow(new LostItemNotFoundException(999L));

        mockMvc.perform(post("/api/v1/lost-items/999/claims")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"quantity": 1}
                                """))
                .andExpect(status().isNotFound());
    }

    @Test
    void anonymousUsersCannotClaim() throws Exception {
        mockMvc.perform(post("/api/v1/lost-items/7/claims")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"quantity": 1}
                                """))
                .andExpect(status().isUnauthorized());
    }
}
