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
                api(project(":application:library"))
                api(libs.org.jetbrains.kotlinx.kotlinx.coroutines.test)
                api(libs.io.kotest.kotest.assertions.core)
                api(libs.io.kotest.kotest.framework.engine)
            }
        }

        val commonTest by getting {
            dependencies {
            }
        }

        val jvmMain by getting {
            dependencies {
                implementation(libs.org.jetbrains.kotlinx.kotlinx.coroutines.debug)
                implementation(libs.io.kotest.kotest.runner.junit5)
            }
        }

        val jvmTest by getting {
            dependencies {
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
