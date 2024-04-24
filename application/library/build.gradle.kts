plugins {
    alias(libs.plugins.org.jetbrains.kotlin.multiplatform)
    alias(libs.plugins.org.jetbrains.kotlin.atomicfu)
    alias(libs.plugins.org.jetbrains.kotlin.plugin.serialization)
    alias(libs.plugins.io.kotest.multiplatform)
    alias(libs.plugins.org.jetbrains.kotlinx.kover)
    alias(libs.plugins.org.jmailen.kotlinter)
}

group = "com.${rootProject.name}"
version = "0.0-SNAPSHOT"

kotlin {
    val jdkVersion = project.property("local.jdk.version").toString().toInt()

    jvmToolchain(jdkVersion)

    jvm {
        compilations.configureEach {
            kotlinOptions.freeCompilerArgs += listOf("-Xjdk-release=$jdkVersion")
        }
    }

    js {
        browser()
    }

    sourceSets {
        all {
            languageSettings.apply {
                progressiveMode = true
                enableLanguageFeature("ExpectActualClasses")
                providers.gradleProperty("local.kotlin.optIn").orNull?.let { optInList ->
                    optInList.split(',').forEach { optIn(it) }
                }
            }
        }

        val commonMain by getting {
            dependencies {
                api(libs.org.jetbrains.kotlinx.kotlinx.coroutines.core)
                api(libs.org.jetbrains.kotlinx.kotlinx.serialization.core)
                api(libs.org.jetbrains.kotlinx.atomicfu)
                api(libs.org.jetbrains.kotlinx.kotlinx.datetime)
            }
        }

        val commonTest by getting {
            dependencies {
                implementation(libs.org.jetbrains.kotlinx.kotlinx.coroutines.test)
                implementation(libs.io.kotest.kotest.assertions.core)
                implementation(libs.io.kotest.kotest.framework.engine)
            }
        }

        val jvmMain by getting {
            dependencies {
                implementation(libs.ch.qos.logback.logback.classic)
            }
        }

        val jvmTest by getting {
            dependencies {
                implementation(libs.io.kotest.kotest.runner.junit5)
            }
        }
    }
}

apply(from = "$rootDir/gradle-scripts/junit-tests.gradle.kts")

kover {
    if (System.getProperty("application.coverage") != "true") disable()
}

kotlinter {
    ignoreFailures = false
    reporters = arrayOf("checkstyle", "plain")
}
