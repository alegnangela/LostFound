package com.crescendo.lostfound.config;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.core.convert.converter.Converter;
import org.springframework.security.authentication.AbstractAuthenticationToken;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.InvalidBearerTokenException;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationConverter;
import org.springframework.security.oauth2.server.resource.authentication.JwtGrantedAuthoritiesConverter;
import org.springframework.security.provisioning.InMemoryUserDetailsManager;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.util.StringUtils;

/**
 * Two authentication mechanisms, chosen by profile, in front of one set of authorization rules:
 *
 * <ul>
 *   <li><b>Default (production):</b> an OAuth2 resource server accepting only Microsoft Entra ID
 *       (Azure AD) access tokens issued for this API - signature, expiry, issuer and audience are
 *       validated by Spring Boot from {@code spring.security.oauth2.resourceserver.jwt.*}. The
 *       {@code oid} claim becomes the caller's identity and the {@code roles} claim (App Roles
 *       assigned in the app registration) becomes {@code ROLE_*} authorities.</li>
 *   <li><b>{@code local} profile:</b> HTTP Basic with two in-memory users, so the application runs
 *       on a developer machine without an Azure tenant. Never enable it in a deployed environment.</li>
 * </ul>
 *
 * <p>Controllers and services never see tokens or credentials, only an {@code Authentication}
 * whose name is the user id and whose authorities are the roles - identical under both profiles.
 *
 * <p>CSRF protection is disabled because this is a stateless JSON API with no
 * browser session/cookie-based auth (see {@code SessionCreationPolicy.STATELESS});
 * CSRF matters when a browser automatically attaches credentials (cookies) to
 * cross-site requests, which cannot happen here.
 *
 * <p>Role checks live on the controllers themselves via {@code @PreAuthorize}
 * ({@link EnableMethodSecurity} below), not as URL patterns here - the filter
 * chain's job is reduced to the two things a per-method annotation cannot
 * express: which paths need no authentication at all ({@code permitAll}), and
 * the fail-closed default that everything else requires at least a valid
 * identity ({@code anyRequest().authenticated()}) before a role is even
 * considered.
 */
@Configuration
@EnableMethodSecurity
public class SecurityConfig {

    static final String LOCAL_PROFILE = "local";

    /**
     * The user's object id: immutable and the same across every app registration in the tenant,
     * unlike {@code sub}, which is pairwise per application - so it is the identifier Microsoft
     * recommends for keying user data.
     */
    static final String USER_ID_CLAIM = "oid";

    /** App Roles assigned to the user in the app registration, e.g. {@code ["USER"]}. */
    static final String ROLES_CLAIM = "roles";

    private static HttpSecurity applyCommonRules(HttpSecurity http) throws Exception {
        return http
                .csrf(AbstractHttpConfigurer::disable)
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers(
                                "/swagger-ui/**", "/v3/api-docs/**",
                                "/actuator/health", "/actuator/health/**")
                        .permitAll()
                        .anyRequest().authenticated());
    }

    /**
     * Maps an already-validated Azure AD access token to an {@code Authentication}: {@code oid} as
     * its name, {@code roles} as {@code ROLE_*} authorities. A token without {@code oid} (e.g. an
     * app-only token not representing a user) is rejected with 401, since claims must always be
     * recorded under a real user id.
     */
    static Converter<Jwt, AbstractAuthenticationToken> azureAdJwtAuthenticationConverter() {
        JwtGrantedAuthoritiesConverter rolesConverter = new JwtGrantedAuthoritiesConverter();
        rolesConverter.setAuthoritiesClaimName(ROLES_CLAIM);
        rolesConverter.setAuthorityPrefix("ROLE_");

        JwtAuthenticationConverter delegate = new JwtAuthenticationConverter();
        delegate.setJwtGrantedAuthoritiesConverter(rolesConverter);
        delegate.setPrincipalClaimName(USER_ID_CLAIM);

        return jwt -> {
            if (!StringUtils.hasText(jwt.getClaimAsString(USER_ID_CLAIM))) {
                throw new InvalidBearerTokenException("Access token has no '" + USER_ID_CLAIM + "' claim");
            }
            return delegate.convert(jwt);
        };
    }

    @Configuration(proxyBeanMethods = false)
    @Profile("!" + LOCAL_PROFILE)
    static class AzureAdJwtSecurity {

        @Bean
        SecurityFilterChain jwtFilterChain(HttpSecurity http) throws Exception {
            return applyCommonRules(http)
                    .oauth2ResourceServer(oauth2 -> oauth2
                            .jwt(jwt -> jwt.jwtAuthenticationConverter(azureAdJwtAuthenticationConverter())))
                    .build();
        }
    }

    @Configuration(proxyBeanMethods = false)
    @Profile(LOCAL_PROFILE)
    @EnableConfigurationProperties(SecurityUsersProperties.class)
    static class LocalBasicAuthSecurity {

        @Bean
        SecurityFilterChain basicAuthFilterChain(HttpSecurity http) throws Exception {
            return applyCommonRules(http)
                    .httpBasic(Customizer.withDefaults())
                    .build();
        }

        @Bean
        UserDetailsService userDetailsService(PasswordEncoder passwordEncoder, SecurityUsersProperties users) {
            UserDetails admin = User.withUsername("admin")
                    .password(passwordEncoder.encode(users.adminPassword()))
                    .roles("ADMIN")
                    .build();
            UserDetails user = User.withUsername("user")
                    .password(passwordEncoder.encode(users.userPassword()))
                    .roles("USER")
                    .build();
            return new InMemoryUserDetailsManager(admin, user);
        }

        @Bean
        PasswordEncoder passwordEncoder() {
            return new BCryptPasswordEncoder();
        }
    }
}
