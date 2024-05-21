package library.core

actual val loggingInsteadEnabled: Boolean
    get() = loggingInsteadEnabledJvm

private var loggingInsteadEnabledJvm = System.getProperty("application.test.debugTrace.loggingInsteadEnabled") == "true"
