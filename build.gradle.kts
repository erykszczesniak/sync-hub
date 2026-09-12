import io.gitlab.arturbosch.detekt.Detekt
import org.jlleitschuh.gradle.ktlint.KtlintExtension

plugins {
    kotlin("jvm") version "2.2.20" apply false
    kotlin("plugin.spring") version "2.2.20" apply false
    kotlin("plugin.jpa") version "2.2.20" apply false
    id("org.springframework.boot") version "3.5.16" apply false
    id("org.jlleitschuh.gradle.ktlint") version "13.1.0" apply false
    id("io.gitlab.arturbosch.detekt") version "1.23.8" apply false
}

allprojects {
    group = "com.erykszczesniak"
    version = "0.1.0"

    repositories {
        mavenCentral()
    }
}

subprojects {
    apply(plugin = "org.jetbrains.kotlin.jvm")
    apply(plugin = "org.jlleitschuh.gradle.ktlint")
    apply(plugin = "io.gitlab.arturbosch.detekt")
    apply(plugin = "jacoco")

    configure<JavaPluginExtension> {
        toolchain {
            languageVersion = JavaLanguageVersion.of(21)
        }
    }

    configure<KtlintExtension> {
        version.set("1.5.0")
        android.set(false)
        filter {
            exclude { it.file.path.contains("/build/") }
        }
    }

    // detekt: default rule set plus the small overrides in config/detekt/detekt.yml.
    configure<io.gitlab.arturbosch.detekt.extensions.DetektExtension> {
        buildUponDefaultConfig = true
        allRules = false
        config.setFrom(rootProject.files("config/detekt/detekt.yml"))
    }

    // detekt 1.23.x is compiled against Kotlin 2.0.21; keep its own classpath on that Kotlin so it runs
    // under the project's Kotlin 2.2.x (documented workaround on detekt.dev). This also is why the Spring
    // Boot BOM is applied as a Gradle platform per module instead of via the dependency-management plugin:
    // that plugin rewrites versions in every configuration, including detekt's.
    configurations.matching { it.name == "detekt" }.all {
        resolutionStrategy.eachDependency {
            if (requested.group == "org.jetbrains.kotlin") {
                useVersion("2.0.21")
            }
        }
    }

    tasks.withType<Detekt>().configureEach {
        jvmTarget = "21"
        reports {
            html.required.set(true)
            xml.required.set(true)
        }
    }

    tasks.withType<org.jetbrains.kotlin.gradle.tasks.KotlinCompile>().configureEach {
        compilerOptions {
            freeCompilerArgs.addAll("-Xjsr305=strict")
        }
    }

    tasks.withType<Test>().configureEach {
        useJUnitPlatform()
        testLogging {
            events("failed", "skipped")
            exceptionFormat = org.gradle.api.tasks.testing.logging.TestExceptionFormat.FULL
        }
        finalizedBy(tasks.named("jacocoTestReport"))
    }

    tasks.withType<JacocoReport>().configureEach {
        dependsOn(tasks.withType<Test>())
        reports {
            xml.required.set(true)
            html.required.set(true)
        }
    }
}
