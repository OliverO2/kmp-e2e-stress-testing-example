plugins {
    alias(libs.plugins.org.jetbrains.kotlin.multiplatform)
    alias(libs.plugins.org.jetbrains.kotlin.atomicfu)
    alias(libs.plugins.org.jetbrains.compose)
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
            kotlinOptions.freeCompilerArgs += listOf(
                "-Xjdk-release=$jdkVersion",
                // NOTE: The following option will leak memory – https://youtrack.jetbrains.com/issue/KT-48678
                "-Xdebug" // Coroutine debugger: disable "was optimised out"
            )
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

        val jvmMain by getting {
            dependencies {
                implementation(project(":application:backend"))
                implementation(project(":application:frontend-core"))
                implementation(compose.runtime)
            }
        }

        val jvmTest by getting {
            dependencies {
                implementation(project(":application:library-testing"))
                implementation(libs.io.ktor.ktor.server.test.host)
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
    // kotlinCompilerPluginArgs.add("reportsDestination=$projectDir/build/reports")
}

apply(from = "$rootDir/gradle-scripts/junit-tests.gradle.kts")

kover {
    if (System.getProperty("application.coverage") != "true") disable()
}

kotlinter {
    ignoreFailures = false
    reporters = arrayOf("checkstyle", "plain")
}
