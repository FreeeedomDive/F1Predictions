plugins {
    id("f1.spring-conventions")
    id("org.springframework.boot")
}

dependencies {
    implementation(project(":schema"))

    implementation(libs.spring.boot.starter)
    implementation(libs.spring.boot.starter.jdbc)
    implementation(libs.spring.boot.starter.flyway)
    implementation(libs.flyway.database.postgresql)

    runtimeOnly(libs.postgresql)
}
