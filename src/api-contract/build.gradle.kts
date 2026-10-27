plugins {
    id("f1.spring-conventions")
    id("f1.testing")
}

dependencies {
    // Only the service contract: DTOs and the @HttpExchange interface the Discord module calls.
    // In phase 2 the same interface is backed by an HTTP client instead of the in-process adapter.
    api(libs.spring.web)
}
