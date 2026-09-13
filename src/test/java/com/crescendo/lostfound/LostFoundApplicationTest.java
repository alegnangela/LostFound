package com.crescendo.lostfound;

import com.crescendo.lostfound.support.TestAzureAdTokens;
import com.crescendo.lostfound.support.TestPdfBuilder;
import com.crescendo.lostfound.web.dto.ClaimResponse;
import com.crescendo.lostfound.web.dto.ImportResponse;
import com.crescendo.lostfound.web.dto.LostItemResponse;
import com.crescendo.lostfound.web.dto.LostItemWithClaimsResponse;
import com.crescendo.lostfound.web.dto.PageResponse;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.boot.resttestclient.TestRestTemplate;
import org.springframework.boot.resttestclient.autoconfigure.AutoConfigureTestRestTemplate;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.core.io.InputStreamResource;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.groups.Tuple.tuple;

/**
 * Exercises the whole assignment end to end against a real (embedded) HTTP server,
 * H2 database and Spring Security stack, authenticating with Azure AD-shaped JWTs
 * signed by a test key: admin uploads a PDF, users read and claim items, and an
 * admin reads back who claimed what - with the mock User Service resolving display
 * names along the way.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureTestRestTemplate
@AutoConfigureTestDatabase
@ActiveProfiles("test")
@Import(TestAzureAdTokens.DecoderConfiguration.class)
class LostFoundApplicationTest {

    private static final String ALICE = "11111111-1111-1111-1111-111111111111";
    private static final String BOB = "22222222-2222-2222-2222-222222222222";
    private static final String ADMIN = "aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa";

    private final String aliceToken = TestAzureAdTokens.accessToken(ALICE, "USER");
    private final String bobToken = TestAzureAdTokens.accessToken(BOB, "USER");
    private final String adminToken = TestAzureAdTokens.accessToken(ADMIN, "ADMIN");

    @Autowired
    private TestRestTemplate restTemplate;

    @Test
    void fullLostAndFoundWorkflow() {
        ImportResponse importResponse = uploadSampleFile();
        assertThat(importResponse.itemsImported()).isEqualTo(4);

        List<LostItemResponse> items = get(aliceToken, "/api/v1/lost-items", LOST_ITEM_PAGE).getBody().content();
        assertThat(items).hasSize(4);

        // Second page of size 3 holds only the last of the 4 items, in id order.
        PageResponse<LostItemResponse> secondPage =
                get(aliceToken, "/api/v1/lost-items?page=1&size=3", LOST_ITEM_PAGE).getBody();
        assertThat(secondPage.content()).extracting(LostItemResponse::id).containsExactly(items.get(3).id());
        assertThat(secondPage.totalElements()).isEqualTo(4);
        assertThat(secondPage.totalPages()).isEqualTo(2);

        LostItemResponse headphones = items.stream()
                .filter(i -> i.itemName().equals("Headphones"))
                .findFirst().orElseThrow();
        assertThat(headphones.quantity()).isEqualTo(2);
        assertThat(headphones.remainingQuantity()).isEqualTo(2);

        // Alice claims as herself; a spoofed userId in the body is ignored - the claim is recorded
        // under the oid from her token, not "mallory".
        ResponseEntity<ClaimResponse> aliceClaim = postJson(aliceToken, claimsUrl(headphones),
                "{\"userId\": \"mallory\", \"quantity\": 1}", ClaimResponse.class);
        assertThat(aliceClaim.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat(aliceClaim.getBody().userId()).isEqualTo(ALICE);

        ResponseEntity<ClaimResponse> bobClaim = postJson(bobToken, claimsUrl(headphones),
                "{\"quantity\": 1}", ClaimResponse.class);
        assertThat(bobClaim.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat(bobClaim.getBody().userId()).isEqualTo(BOB);

        // A third claim should now be rejected: both units are already spoken for.
        ResponseEntity<String> overClaim = postJson(aliceToken, claimsUrl(headphones), "{\"quantity\": 1}", String.class);
        assertThat(overClaim.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);

        // An ADMIN-only account manages lost items but cannot claim them.
        ResponseEntity<String> adminClaim = postJson(adminToken, claimsUrl(headphones), "{\"quantity\": 1}", String.class);
        assertThat(adminClaim.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);

        PageResponse<LostItemWithClaimsResponse> adminPage =
                get(adminToken, "/api/v1/admin/lost-items/claims", ITEM_WITH_CLAIMS_PAGE).getBody();
        LostItemWithClaimsResponse headphonesAdminView = adminPage.content().stream()
                .filter(i -> i.itemName().equals("Headphones"))
                .findFirst().orElseThrow();
        assertThat(headphonesAdminView.remainingQuantity()).isZero();
        assertThat(headphonesAdminView.claimants())
                .extracting(c -> c.userId(), c -> c.userName())
                .containsExactlyInAnyOrder(tuple(ALICE, "User-" + ALICE), tuple(BOB, "User-" + BOB));

        // A regular user must not be able to reach admin-only endpoints.
        ResponseEntity<String> forbidden = get(aliceToken, "/api/v1/admin/lost-items/claims", STRING);
        assertThat(forbidden.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
    }

    @Test
    void requestsWithoutAUsableAccessTokenAreRejected() {
        assertThat(get(null, "/api/v1/lost-items", STRING).getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        assertThat(get("not-a-jwt", "/api/v1/lost-items", STRING).getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);

        String tokenWithoutUserId = TestAzureAdTokens.accessToken(Map.of("roles", List.of("USER")));
        assertThat(get(tokenWithoutUserId, "/api/v1/lost-items", STRING).getStatusCode())
                .isEqualTo(HttpStatus.UNAUTHORIZED);
    }

    private static final ParameterizedTypeReference<PageResponse<LostItemResponse>> LOST_ITEM_PAGE =
            new ParameterizedTypeReference<>() {};
    private static final ParameterizedTypeReference<PageResponse<LostItemWithClaimsResponse>> ITEM_WITH_CLAIMS_PAGE =
            new ParameterizedTypeReference<>() {};
    private static final ParameterizedTypeReference<String> STRING = new ParameterizedTypeReference<>() {};

    private static String claimsUrl(LostItemResponse item) {
        return "/api/v1/lost-items/" + item.id() + "/claims";
    }

    private <T> ResponseEntity<T> get(String token, String url, ParameterizedTypeReference<T> type) {
        return restTemplate.exchange(url, HttpMethod.GET, new HttpEntity<>(bearer(token)), type);
    }

    private <T> ResponseEntity<T> postJson(String token, String url, String json, Class<T> type) {
        HttpHeaders headers = bearer(token);
        headers.setContentType(MediaType.APPLICATION_JSON);
        return restTemplate.exchange(url, HttpMethod.POST, new HttpEntity<>(json, headers), type);
    }

    private static HttpHeaders bearer(String token) {
        HttpHeaders headers = new HttpHeaders();
        if (token != null) {
            headers.setBearerAuth(token);
        }
        return headers;
    }

    private ImportResponse uploadSampleFile() {
        MultiValueMap<String, Object> body = new LinkedMultiValueMap<>();
        body.add("file", pdfResource());

        HttpHeaders headers = bearer(adminToken);
        headers.setContentType(MediaType.MULTIPART_FORM_DATA);

        ResponseEntity<ImportResponse> response = restTemplate.exchange("/api/v1/admin/lost-items/import",
                HttpMethod.POST, new HttpEntity<>(body, headers), ImportResponse.class);
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        return response.getBody();
    }

    private InputStreamResource pdfResource() {
        InputStream pdf = TestPdfBuilder.buildPdf(List.of(
                "ItemName: Laptop", "Quantity: 1", "Place: Taxi", "",
                "ItemName: Headphones", "Quantity: 2", "Place: Railway station", "",
                "ItemName: Jewels", "Quantity: 4", "Place: Airport", "",
                "ItemName: Laptop", "Quantity: 1", "Place: Airport"));
        return new InputStreamResource(pdf) {
            @Override
            public String getFilename() {
                return "lost-items.pdf";
            }

            @Override
            public long contentLength() {
                try {
                    return pdf.available();
                } catch (IOException e) {
                    throw new UncheckedIOException(e);
                }
            }
        };
    }
}
