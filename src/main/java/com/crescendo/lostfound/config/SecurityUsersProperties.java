package com.crescendo.lostfound.config;

import jakarta.validation.constraints.NotBlank;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

/**
 * Passwords for the {@code local} profile's in-memory HTTP Basic users, bound from {@code app.security.*} so they are
 * supplied by the environment ({@code ADMIN_PASSWORD} / {@code USER_PASSWORD}) rather than
 * hardcoded in source.
 *
 * <p>{@code @NotBlank} fails startup on an empty value: an environment variable that is set but
 * empty is bound as-is by Spring instead of falling back to the default, which would otherwise
 * silently create a user with an empty password.
 */
@Validated
@ConfigurationProperties("app.security")
public record SecurityUsersProperties(@NotBlank String adminPassword, @NotBlank String userPassword) {

    /** Masks the secrets so they never end up in logs or error messages. */
    @Override
    public String toString() {
        return "SecurityUsersProperties[adminPassword=***, userPassword=***]";
    }
}
