package library.testing

import io.kotest.core.config.AbstractProjectConfig
import io.kotest.core.extensions.Extension
import io.kotest.core.extensions.SpecExtension
import io.kotest.core.extensions.TestCaseExtension
import io.kotest.core.spec.Spec
import io.kotest.core.test.TestCase
import io.kotest.core.test.TestResult
import kotlinx.coroutines.withContext
import library.core.DebugTrace
import library.telemetry.Telemetry
import kotlin.coroutines.CoroutineContext

private val logger = Telemetry.logger("library.testing.CommonTestConfigurationKt")

/**
 * The common test configuration.
 *
 * Usage:
 * ```
 * object TestConfiguration : CommonTestConfiguration(LoggingConfiguration(/*...*/))
 * ```
 *
 * This abstract class will not be automatically picked up by Kotest via [AbstractProjectConfig]. A derivative
 * based on this class must be created for each test target as shown above.
 * Note: To use objects on K/JS, they must be referenced at least once in code that executes, otherwise they will be
 * discarded (with the IDE warning "object ... is never used"). Class derivatives of AbstractProjectConfig will
 * always be automatically instantiated by the Kotest compiler plugin.
 */
abstract class CommonTestConfiguration(
    private val debugTraceEnabled: Boolean = true
) : AbstractProjectConfig() {

    override fun extensions(): List<Extension> = listOf(DebugTraceTestCaseExtension)

    override suspend fun beforeProject() {
        DebugTrace.enabled = debugTraceEnabled
        logger.info { "testVariant=$testVariant, DebugTrace.enabled=${DebugTrace.enabled}" }
    }

    /**
     * A test project extension enabling DebugTrace.
     */
    object DebugTraceTestCaseExtension : TestCaseExtension {
        override suspend fun intercept(testCase: TestCase, execute: suspend (TestCase) -> TestResult): TestResult =
            execute(testCase).also {
                if (DebugTrace.enabled) {
                    DebugTrace.clearAll()
                }
            }
    }
}

/**
 * A test spec and test case extension providing a coroutine context to a spec and each of its test cases.
 */
open class CoroutineContextTestSpecExtension(private val coroutineContext: CoroutineContext) :
    SpecExtension, TestCaseExtension {

    override suspend fun intercept(spec: Spec, execute: suspend (Spec) -> Unit) = withContext(coroutineContext) {
        execute(spec)
    }

    override suspend fun intercept(testCase: TestCase, execute: suspend (TestCase) -> TestResult): TestResult =
        withContext(coroutineContext) {
            execute(testCase)
        }
}

enum class TestVariant {
    QUICK,
    EXTENDED,
    INFINITE;

    /**
     * Returns the value of the first key matching this [TestVariant], or the [default].
     *
     * Example:
     * ```
     * val invocationCount = testVariant.dependent(10, TestVariant.EXTENDED to 20)
     * ```
     */
    fun <Type> dependent(default: Type, vararg pairs: Pair<TestVariant, Type>): Type =
        pairs.firstOrNull { this == it.first }?.second ?: default
}

/**
 * The [TestVariant] selected for the current test runner invocation.
 */
expect val testVariant: TestVariant
