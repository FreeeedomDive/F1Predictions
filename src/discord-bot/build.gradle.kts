plugins {
    id("f1.spring-conventions")
    id("f1.testing")
}

dependencies {
    // The Discord module must not see the domain or the database: it talks to the service
    // through api-contract only, so extracting it in phase 2 is a dependency swap.
    implementation(project(":api-contract"))

    implementation(libs.jda)
    implementation(libs.spring.context)
    implementation(libs.spring.boot.starter)
}
