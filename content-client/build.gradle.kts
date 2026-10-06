plugins {
    kotlin("jvm")
    `java-library`
    `maven-publish`
    id("io.spring.dependency-management")
    id("org.jlleitschuh.gradle.ktlint")
}

description = "content-client"

java {
    toolchain {
        languageVersion = JavaLanguageVersion.of(25)
    }
    withSourcesJar()
}

dependencyManagement {
    imports {
        mavenBom("org.springframework.boot:spring-boot-dependencies:4.1.1")
    }
}

extra["microformats2Version"] = "0.1.2"

dependencies {
    // Exposed API: the write/read boundary is mf2, so microformats2 types appear in DTOs.
    api("dev.jacobandersen:microformats2:${property("microformats2Version")}")
    api("org.springframework:spring-web")

    implementation("org.jetbrains.kotlin:kotlin-reflect")
    implementation("tools.jackson.core:jackson-databind")
    implementation("tools.jackson.module:jackson-module-kotlin")
    implementation("com.fasterxml.jackson.core:jackson-annotations")

    // Optional Spring Boot integration (auto-configuration).
    compileOnly("org.springframework.boot:spring-boot-autoconfigure")

    testImplementation("org.springframework.boot:spring-boot-starter-test")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}

kotlin {
    compilerOptions {
        freeCompilerArgs.addAll("-Xjsr305=strict", "-Xannotation-default-target=param-property")
    }
}

tasks.withType<Test> {
    useJUnitPlatform()
}

ktlint {
    version.set("1.8.0")
}

publishing {
    publications {
        create<MavenPublication>("maven") {
            from(components["java"])
            artifactId = "content-client"
        }
    }
    repositories {
        maven {
            name = "GitHubPackages"
            url = uri("https://maven.pkg.github.com/marchland/bastion")
            credentials {
                username = System.getenv("GITHUB_ACTOR") ?: (project.findProperty("gpr.user") as String?)
                password = System.getenv("GITHUB_TOKEN") ?: (project.findProperty("gpr.token") as String?)
            }
        }
    }
}
