plugins {
    id("f1.spring-conventions")
    id("f1.testing")
}

dependencies {
    api(project(":domain"))
    implementation(project(":api-contract"))

    implementation(libs.spring.context)
    implementation(libs.spring.tx)
    implementation(libs.slf4j.api)
    implementation(libs.micrometer.core)
    implementation(libs.spring.modulith.events.api)
}
