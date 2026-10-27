plugins {
    id("f1.spring-conventions")
    id("f1.testing")
}

dependencies {
    implementation(project(":domain"))
    implementation(project(":application"))
    runtimeOnly(project(":schema"))

    implementation(libs.spring.boot.starter.data.jpa)
    // Declarative clients for Jolpica and OpenF1 are @HttpExchange interfaces; only the
    // annotations are needed here, the proxies are built where the clients get wired.
    implementation(libs.spring.web)

    runtimeOnly(libs.postgresql)
}
