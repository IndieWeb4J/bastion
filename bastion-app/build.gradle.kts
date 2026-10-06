plugins {
    kotlin("jvm")
    kotlin("plugin.spring")
    id("org.springframework.boot")
    id("io.spring.dependency-management")
    id("org.hibernate.orm")
    kotlin("plugin.jpa")
    id("org.jlleitschuh.gradle.ktlint")
}

description = "bastion-app"

java {
    toolchain {
        languageVersion = JavaLanguageVersion.of(25)
    }
}

extra["microformats2Version"] = "0.1.1"

dependencyManagement {
    imports {
        mavenBom("org.apache.logging.log4j:log4j-bom:2.26.1")
    }
}

dependencies {
    // The content contract (write/read/media clients + content event schemas).
    implementation(project(":content-client"))

    implementation("org.springframework.boot:spring-boot-starter-actuator")
    implementation("org.springframework.boot:spring-boot-starter-data-jpa")
    implementation("org.springframework.boot:spring-boot-starter-flyway")
    implementation("org.springframework.boot:spring-boot-starter-webmvc")
    implementation("org.flywaydb:flyway-database-postgresql")
    implementation("org.jetbrains.kotlin:kotlin-reflect")
    implementation("org.jobrunr:jobrunr-spring-boot-4-starter:8.7.0")
    implementation("dev.jacobandersen:microformats2:${property("microformats2Version")}")
    implementation("dev.jacobandersen:beacon-client:0.1.0")
    implementation("dev.jacobandersen:conduit-client:0.1.0")
    implementation("tools.jackson.module:jackson-module-kotlin")
    implementation("org.springframework.boot:spring-boot-starter-security")
    implementation("org.apache.logging.log4j:log4j-api")
    implementation("org.apache.logging.log4j:log4j-core")
    implementation("io.github.oshai:kotlin-logging-jvm:7.0.3")
    implementation("io.nats:jnats:2.26.4")
    implementation("com.github.slugify:slugify:4.0.1")
    runtimeOnly("com.github.slugify:slugify") {
        capabilities {
            requireCapability("com.github.slugify:slugify-transliterator")
        }
    }
    implementation("software.amazon.awssdk:s3:2.10.53")
    testImplementation("org.springframework.security:spring-security-test")
    testImplementation("org.mockito.kotlin:mockito-kotlin:6.2.0")
    developmentOnly("org.springframework.boot:spring-boot-devtools")
    runtimeOnly("org.postgresql:postgresql")
    testImplementation("org.springframework.boot:spring-boot-starter-actuator-test")
    testImplementation("org.springframework.boot:spring-boot-starter-data-jpa-test")
    testImplementation("org.springframework.boot:spring-boot-starter-flyway-test")
    testImplementation("org.springframework.boot:spring-boot-starter-webmvc-test")
    testImplementation("org.springframework.boot:spring-boot-testcontainers")
    testImplementation("org.jetbrains.kotlin:kotlin-test-junit5")
    testImplementation("org.testcontainers:testcontainers-junit-jupiter")
    testImplementation("org.testcontainers:testcontainers-postgresql")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}

kotlin {
    compilerOptions {
        freeCompilerArgs.addAll("-Xjsr305=strict", "-Xannotation-default-target=param-property")
    }
}

hibernate {
    enhancement {
    }
}

tasks.bootJar {
    archiveFileName.set("bastion.jar")
}

allOpen {
    annotation("jakarta.persistence.Entity")
    annotation("jakarta.persistence.MappedSuperclass")
    annotation("jakarta.persistence.Embeddable")
}

tasks.withType<Test> {
    useJUnitPlatform()
}

ktlint {
    version.set("1.8.0")
}
