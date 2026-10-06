plugins {
    kotlin("jvm") version "2.3.21" apply false
    kotlin("plugin.spring") version "2.3.21" apply false
    id("org.springframework.boot") version "4.1.1" apply false
    id("io.spring.dependency-management") version "1.1.7" apply false
    id("org.hibernate.orm") version "7.4.5.Final" apply false
    kotlin("plugin.jpa") version "2.3.21" apply false
    id("org.jlleitschuh.gradle.ktlint") version "12.2.0" apply false
}

allprojects {
    group = "dev.jacobandersen"
    version = rootProject.file("version.txt").readText().trim()
}

subprojects {
    repositories {
        mavenCentral()
        mavenLocal()
        maven {
            name = "Mf24jGitHubPackages"
            url = uri("https://maven.pkg.github.com/jacobsandersen/mf24j")
            credentials {
                username = System.getenv("GITHUB_ACTOR") ?: (project.findProperty("gpr.user") as String?)
                password = System.getenv("GITHUB_TOKEN") ?: (project.findProperty("gpr.token") as String?)
            }
        }
        maven {
            name = "BeaconGitHubPackages"
            url = uri("https://maven.pkg.github.com/jacobsandersen/beacon")
            credentials {
                username = System.getenv("GITHUB_ACTOR") ?: (project.findProperty("gpr.user") as String?)
                password = System.getenv("GITHUB_TOKEN") ?: (project.findProperty("gpr.token") as String?)
            }
        }
        maven {
            name = "ConduitGitHubPackages"
            url = uri("https://maven.pkg.github.com/jacobsandersen/conduit")
            credentials {
                username = System.getenv("GITHUB_ACTOR") ?: (project.findProperty("gpr.user") as String?)
                password = System.getenv("GITHUB_TOKEN") ?: (project.findProperty("gpr.token") as String?)
            }
        }
    }
}
