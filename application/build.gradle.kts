plugins {
    alias(libs.plugins.org.jetbrains.kotlin.multiplatform) apply false
    alias(libs.plugins.org.jetbrains.compose) apply false
    alias(libs.plugins.org.jetbrains.kotlinx.kover)
}

dependencies {
    kover(project(":application:library"))
    kover(project(":application:library-testing"))
    kover(project(":application:core"))
    kover(project(":application:backend"))
    kover(project(":application:frontend-core"))
    kover(project(":application:integration-test"))
}

kover {
    if (System.getProperty("application.coverage") != "true") disable()
}
