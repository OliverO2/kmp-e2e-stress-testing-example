plugins {
    alias(libs.plugins.org.jetbrains.kotlin.multiplatform)
    alias(libs.plugins.org.jetbrains.kotlin.atomicfu)
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
                providers.gradleProperty("local.kotlin.optIn").orNull?.let { optInList ->
                    optInList.split(',').forEach { optIn(it) }
                }
            }
        }

        val commonMain by getting {
            dependencies {
                api(project(":application:core"))
                implementation(libs.org.jetbrains.kotlinx.kotlinx.serialization.protobuf)
                implementation(libs.io.ktor.ktor.client.core)
                implementation(libs.io.ktor.ktor.websockets)
                api(libs.org.jetbrains.compose.runtime)
            }
        }

        val commonTest by getting {
            dependencies {
                implementation(project(":application:library-testing"))
            }
        }

        val jvmMain by getting {
            dependencies {
                implementation(libs.io.ktor.ktor.client.cio)
            }
        }

        val jvmTest by getting {
            dependencies {
            }
        }
    }
}

kover {
    if (System.getProperty("application.coverage") != "true") disable()
}

kotlinter {
    ignoreFailures = false
    reporters = arrayOf("checkstyle", "plain")
}
