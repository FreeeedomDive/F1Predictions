plugins {
    id("f1.java-common")
    id("f1.testing")
}

dependencies {
    testImplementation(platform("org.springframework.boot:spring-boot-dependencies:${libs.versions.spring.boot.get()}"))
}
