package com.crescendo.lostfound.config;

import com.crescendo.lostfound.service.ClaimService;
import com.crescendo.lostfound.service.LostItemService;
import com.crescendo.lostfound.web.ClaimController;
import com.crescendo.lostfound.web.LostItemController;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.data.domain.Page;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.httpBasic;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** The {@code local} profile swaps Azure AD tokens for HTTP Basic users, keeping the same authorization rules. */
@WebMvcTest(controllers = {LostItemController.class, ClaimController.class})
@Import(SecurityConfig.class)
@ActiveProfiles("local")
class LocalProfileSecurityTest {

    @Autowired
    private MockMvc mockMvc;
    @Autowired
    private SecurityUsersProperties users;
    @MockitoBean
    private LostItemService lostItemService;
    @MockitoBean
    private ClaimService claimService;

    @Test
    void localUserAuthenticatesWithHttpBasic() throws Exception {
        when(lostItemService.findAll(anyInt(), anyInt())).thenReturn(Page.empty());

        mockMvc.perform(get("/api/v1/lost-items").with(httpBasic("user", users.userPassword())))
                .andExpect(status().isOk());
    }

    @Test
    void wrongPasswordIsRejected() throws Exception {
        mockMvc.perform(get("/api/v1/lost-items").with(httpBasic("user", "wrong-password")))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void bearerTokensAreNotAcceptedUnderTheLocalProfile() throws Exception {
        mockMvc.perform(get("/api/v1/lost-items").header(HttpHeaders.AUTHORIZATION, "Bearer some-token"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void localAdminCannotClaim() throws Exception {
        mockMvc.perform(post("/api/v1/lost-items/7/claims")
                        .with(httpBasic("admin", users.adminPassword()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"quantity": 1}
                                """))
                .andExpect(status().isForbidden());
        verifyNoInteractions(claimService);
    }
}
