package library.testing

import library.telemetry.LogEvent
import library.telemetry.LoggingConfiguration
import library.telemetry.activateGlobally

/**
 * The project-wide test configuration.
 */
class JsTestConfiguration : CommonTestConfiguration() {
    init {
        LoggingConfiguration("JS-test", ::emitViaPrintlnPlusNewline).activateGlobally()
    }
}

actual val testVariant = TestVariant.QUICK

/**
 * WORKAROUND: KT-48292: KJS / IR: `println` doesn't move to a new line in tests
 */
private fun emitViaPrintlnPlusNewline(logEvent: LogEvent) {
    println(logEvent.completeText() + "\n")
}
