plugins {
    id("org.gradle.toolchains.foojay-resolver-convention") version "1.0.0"
}

rootProject.name = "f1-predictions"

include(
    ":domain",
    ":application",
    ":infrastructure",
    ":api-contract",
    ":discord-bot",
    ":bootstrap",
    ":schema",
    ":migrator",
)
