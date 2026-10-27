plugins {
    id("f1.spring-conventions")
    id("f1.testing")
}

dependencies {
    api(project(":domain"))

    implementation(libs.spring.context)
    implementation(libs.spring.tx)
    implementation(libs.spring.modulith.events.api)
}
