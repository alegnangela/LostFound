package com.crescendo.lostfound.service.userdirectory;

import org.springframework.stereotype.Component;

/**
 * Stand-in for a real User Service client (e.g. a Feign/RestClient call to
 * {@code GET /users/{id}} with timeouts and retries). The assignment asks for
 * a mock here, so this intentionally has no HTTP call, config, or caching -
 * swapping in a real implementation only requires a new
 * {@link UserDirectoryClient} bean, since callers only depend on the interface.
 */
@Component
public class MockUserDirectoryClient implements UserDirectoryClient {

    @Override
    public String getUserName(String userId) {
        return "User-" + userId;
    }
}
