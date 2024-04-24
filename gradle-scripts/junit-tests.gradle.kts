tasks.withType<Test>().configureEach {
    // https://docs.gradle.org/current/userguide/java_testing.html
    // NOTE: Tests launched via the Kotest IntelliJ plugin will ignore the Gradle configuration.
    // https://kotest.io/docs/intellij/intellij-properties.html
    useJUnitPlatform()
    maxHeapSize = "8G"
    jvmArgs = listOf(
        "-Xss1G"
        // ,"-Xlog:gc*=info:file=${layout.buildDirectory.file("gc.log")}:tags,time,uptime,level"
    )
    // Pass "application.test.*" system properties from the Gradle invocation to the test launcher.
    // https://junit.org/junit5/docs/current/user-guide/#running-tests-config-params
    for ((name, value) in System.getProperties()) {
        if (name is String && name.startsWith("application.test.")) {
            systemProperty(name, value)
        }
    }
    // Show stdout/stderr and stack traces on console – https://stackoverflow.com/q/65573633/2529022
    testLogging {
        events("PASSED", "FAILED", "SKIPPED")
        exceptionFormat = org.gradle.api.tasks.testing.logging.TestExceptionFormat.FULL
        showStandardStreams = true
        // showStackTraces = true
    }
    filter {
        @Suppress("RemoveExplicitTypeArguments", "RedundantSuppression")
        listOf<String>(
            // "serialization.CommonSerializationTests*",
            // "serialization.JvmSerializationTests*",
            // "backend.objects.SubscriptionTests*",
            // "backend.objects.TransactionTests*",
            // "backend.objects.VersionedObjectTests*",
        ).forEach {
            includeTestsMatching("${rootProject.name}.$it")
        }
    }
}
