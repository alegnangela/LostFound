package com.crescendo.lostfound.config;

import org.junit.jupiter.api.Test;
import org.springframework.core.convert.converter.Converter;
import org.springframework.security.authentication.AbstractAuthenticationToken;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.InvalidBearerTokenException;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class AzureAdJwtAuthenticationConverterTest {

    private final Converter<Jwt, AbstractAuthenticationToken> converter =
            SecurityConfig.azureAdJwtAuthenticationConverter();

    @Test
    void usesTheObjectIdAsTheUserIdAndAppRolesAsRoleAuthorities() {
        Jwt jwt = token()
                .subject("pairwise-subject-not-the-user-id")
                .claim("oid", "11111111-1111-1111-1111-111111111111")
                .claim("roles", List.of("USER", "ADMIN"))
                .build();

        AbstractAuthenticationToken authentication = converter.convert(jwt);

        assertThat(authentication.getName()).isEqualTo("11111111-1111-1111-1111-111111111111");
        assertThat(roles(authentication)).containsExactlyInAnyOrder("ROLE_USER", "ROLE_ADMIN");
    }

    @Test
    void delegatedScopesDoNotGrantAnyRole() {
        Jwt jwt = token()
                .claim("oid", "11111111-1111-1111-1111-111111111111")
                .claim("scp", "access_as_user")
                .build();

        AbstractAuthenticationToken authentication = converter.convert(jwt);

        assertThat(roles(authentication)).isEmpty();
        assertThat(authentication.getAuthorities()).extracting(GrantedAuthority::getAuthority)
                .noneMatch(authority -> authority.startsWith("SCOPE_"));
    }

    /**
     * Only {@code ROLE_*} authorities: Spring Security 7 also adds a {@code FACTOR_BEARER} authority
     * recording how the caller authenticated, which is not a role.
     */
    private static List<String> roles(AbstractAuthenticationToken authentication) {
        return authentication.getAuthorities().stream()
                .map(GrantedAuthority::getAuthority)
                .filter(authority -> authority.startsWith("ROLE_"))
                .toList();
    }

    @Test
    void rejectsATokenThatHasNoObjectId() {
        Jwt jwt = token().claim("roles", List.of("USER")).build();

        assertThatThrownBy(() -> converter.convert(jwt))
                .isInstanceOf(InvalidBearerTokenException.class)
                .hasMessageContaining("oid");
    }

    private static Jwt.Builder token() {
        return Jwt.withTokenValue("token").header("alg", "RS256");
    }
}
