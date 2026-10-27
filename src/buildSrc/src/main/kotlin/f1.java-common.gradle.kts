plugins {
    `java-library`
}

group = "com.xdd"
version = "0.0.1-SNAPSHOT"

val libs = the<org.gradle.api.artifacts.VersionCatalogsExtension>().named("libs")

java {
    toolchain {
        languageVersion = JavaLanguageVersion.of(libs.findVersion("java").get().requiredVersion)
    }
}

repositories {
    mavenCentral()
}

tasks.withType<JavaCompile>().configureEach {
    options.compilerArgs.addAll(listOf("-Xlint:all,-serial,-processing", "-parameters"))
}

tasks.withType<Test>().configureEach {
    useJUnitPlatform()
}
