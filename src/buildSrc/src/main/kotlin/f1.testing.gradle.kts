plugins {
    id("f1.java-common")
}

val libs = the<org.gradle.api.artifacts.VersionCatalogsExtension>().named("libs")

dependencies {
    "testImplementation"(libs.findLibrary("junit-jupiter").get())
    "testImplementation"(libs.findLibrary("assertj-core").get())
    "testImplementation"(libs.findLibrary("mockito-core").get())
    "testRuntimeOnly"(libs.findLibrary("junit-platform-launcher").get())
}
