plugins {
    alias(libs.plugins.org.jetbrains.kotlin.multiplatform)
    alias(libs.plugins.org.jetbrains.kotlin.atomicfu)
    alias(libs.plugins.org.jetbrains.compose)
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
        browser {
            commonWebpackConfig { outputFileName = "app.js" }
        }
        binaries.executable()
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
                implementation(project(":application:frontend-core"))
                implementation(compose.ui)
                implementation(compose.foundation)
                implementation(compose.material)
            }
        }

        val jvmMain by getting {
            dependencies {
                implementation(libs.io.ktor.ktor.client.cio)
                implementation(compose.desktop.currentOs)
                implementation(libs.org.jetbrains.kotlinx.kotlinx.coroutines.swing)
            }
        }

        val jsMain by getting {
            dependencies {
            }
        }
    }
}

compose {
    providers.gradleProperty("local.compose.kotlinCompilerPlugin").orNull?.let { composeKotlinCompilerPlugin ->
        kotlinCompilerPlugin.set(composeKotlinCompilerPlugin)
        val kotlinVersion = "${libs.plugins.org.jetbrains.kotlin.multiplatform.get().version}"
        kotlinCompilerPluginArgs.add("suppressKotlinVersionCompatibilityCheck=$kotlinVersion")
    }
    // kotlinCompilerPluginArgs.add("reportsDestination=${layout.buildDirectory.file("reports")}")

    desktop.application.mainClass = "FrontendMainKt"

    experimental {
        web.application {}
    }
}

kotlinter {
    ignoreFailures = false
    reporters = arrayOf("checkstyle", "plain")
}
