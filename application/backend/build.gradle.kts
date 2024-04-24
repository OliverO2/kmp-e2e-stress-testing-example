plugins {
    alias(libs.plugins.org.jetbrains.kotlin.multiplatform)
    alias(libs.plugins.org.jetbrains.kotlin.atomicfu)
    alias(libs.plugins.io.kotest.multiplatform)
    alias(libs.plugins.org.jetbrains.kotlinx.kover)
    alias(libs.plugins.org.jmailen.kotlinter)
    application
}

group = "com.${rootProject.name}"
version = "0.0-SNAPSHOT"

kotlin {
    val jdkVersion = project.property("local.jdk.version").toString().toInt()

    jvmToolchain(jdkVersion)

    jvm {
        withJava()

        compilations.configureEach {
            kotlinOptions.freeCompilerArgs += listOf("-Xjdk-release=$jdkVersion")
        }
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
            }
        }

        val commonTest by getting {
            dependencies {
                implementation(project(":application:library-testing"))
            }
        }

        val jvmMain by getting {
            dependencies {
                implementation(libs.org.jetbrains.kotlinx.kotlinx.serialization.protobuf)
                implementation(libs.io.ktor.ktor.server.netty)
                implementation(libs.io.ktor.ktor.server.websockets)
                implementation(libs.io.ktor.ktor.server.content.negotiation)
                implementation(libs.io.ktor.ktor.server.html.builder)
            }
        }
    }
}

val jvmArgs: MutableList<String> = mutableListOf()

jvmArgs += if (System.getProperty("application.production") == "true") {
    listOf(
        "-Dio.ktor.development=false" // set this explicitly to avoid auto-reload if an "-ea" flag is present
    )
} else {
    listOf(
        "-Dkotlinx.coroutines.debug",
        "-Dio.ktor.development=false" // set this explicitly to avoid auto-reload if an "-ea" flag is present
    )
}

application {
    mainClass.set("BackendMainKt")
    applicationDefaultJvmArgs = jvmArgs
}

apply(from = "$rootDir/gradle-scripts/junit-tests.gradle.kts")

kover {
    if (System.getProperty("application.coverage") != "true") disable()
}

kotlinter {
    ignoreFailures = false
    reporters = arrayOf("checkstyle", "plain")
}
