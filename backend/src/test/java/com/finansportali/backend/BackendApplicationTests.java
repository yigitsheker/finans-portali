package com.finansportali.backend;

import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;

/**
 * Smoke test for the Spring context. Currently disabled because the
 * production config wires an OAuth2 resource-server bean against
 * Keycloak's JWK endpoint, which isn't reachable from a unit-test JVM —
 * the bean fails to initialise and the context never starts.
 *
 * Coverage that matters comes from the slice tests under
 * {@code controller/**Test} and the pure-math unit tests under
 * {@code service/**Test}, which together exercise the actual app code.
 */
@SpringBootTest
@Disabled("Production OAuth2 resource server requires a live JWK endpoint; "
        + "covered indirectly by the @WebMvcTest slices.")
class BackendApplicationTests {

    @Test
    void contextLoads() {
        // intentionally empty
    }
}
