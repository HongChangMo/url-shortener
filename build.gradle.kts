plugins {
    java
    id("org.springframework.boot") version "3.4.0" apply false
    id("io.spring.dependency-management") version "1.1.6" apply false
}

allprojects {
    group = "com.urlshortener"
    version = "0.0.1-SNAPSHOT"

    repositories {
        mavenCentral()
    }
}

subprojects {
    apply(plugin = "java")

    java {
        toolchain { languageVersion = JavaLanguageVersion.of(21) }
    }

    tasks.withType<Test> {
        useJUnitPlatform()
    }
}
