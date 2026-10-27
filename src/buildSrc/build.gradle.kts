plugins {
    `kotlin-dsl`
}

kotlin {
    jvmToolchain(24)
}

repositories {
    mavenCentral()
    gradlePluginPortal()
}

dependencies {
    implementation(libs.spring.boot.gradle.plugin)
    implementation(libs.spring.dep.mgmt.plugin)
}
