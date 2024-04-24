pluginManagement {
    plugins {
        kotlin("multiplatform") apply false
    }
}

plugins {
    // https://github.com/Splitties/refreshVersions/releases
    id("de.fayard.refreshVersions") version "0.60.5"
}

@Suppress("UnstableApiUsage")
dependencyResolutionManagement {
    pluginManagement {
        repositories {
            localDevelopmentRepositories()
            gradlePluginPortal()
            composeDevelopmentRepositories()
        }
    }
    repositories {
        localDevelopmentRepositories()
        mavenCentral()
        composeDevelopmentRepositories()
        google()
    }
}

fun RepositoryHandler.kotlinDevelopmentRepositories() {
    // maven("https://maven.pkg.jetbrains.space/kotlin/p/kotlin/bootstrap")
    // maven("https://maven.pkg.jetbrains.space/kotlin/p/kotlin/dev")
    // maven("https://maven.pkg.jetbrains.space/kotlin/p/kotlin/temporary")
    // maven("https://maven.pkg.jetbrains.space/public/p/kotlinx-coroutines/maven")
    // maven("https://maven.pkg.jetbrains.space/kotlin/p/wasm/experimental")
}

fun RepositoryHandler.composeDevelopmentRepositories() {
    // maven("https://maven.pkg.jetbrains.space/public/p/compose/dev")
    // maven("https://androidx.dev/storage/compose-compiler/repository/")
}

fun RepositoryHandler.localDevelopmentRepositories() {
    maven(url = "${System.getenv("HOME")!!}/.m2/local-repository")
    // mavenLocal()
}

refreshVersions {
    featureFlags {
        enable(de.fayard.refreshVersions.core.FeatureFlag.LIBS)
    }
}

rootProject.name = "example"

include(
    ":application:library",
    ":application:library-testing",
    ":application:core",
    ":application:backend",
    ":application:frontend-core",
    ":application:frontend-ui",
    ":application:integration-test"
)
