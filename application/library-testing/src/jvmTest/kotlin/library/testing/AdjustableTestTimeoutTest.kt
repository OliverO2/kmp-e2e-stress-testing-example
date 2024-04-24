package library.testing

import io.kotest.assertions.shouldFail
import io.kotest.assertions.withClue
import io.kotest.core.annotation.DoNotParallelize
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.string.shouldContain
import kotlinx.coroutines.delay
import library.core.adjustableTimeoutFactorContextElement
import kotlin.time.Duration.Companion.milliseconds

// Run tests sensitive to timing issues independently
@DoNotParallelize
class AdjustableTestTimeoutTest : FunSpec({
    extension(CoroutineContextTestSpecExtension(adjustableTimeoutFactorContextElement(1.5)))

    test("timeout") {
        shouldFail {
            withAdjustableTestTimeout(100.milliseconds, { "Timing out" }) {
                delay(200.milliseconds)
            }
        }
    }

    test("no timeout") {
        withAdjustableTestTimeout(100.milliseconds, { "Not timing out" }) {
            delay(120.milliseconds)
        }
    }

    test("timeout with clue") {
        shouldFail {
            withClue("The clue") {
                withAdjustableTestTimeout(10.milliseconds, { "Timing out" }) {
                    delay(50.milliseconds)
                }
            }
        }.also {
            it.message shouldContain "The clue\nTiming out"
        }
    }
})
