package library.testing

import io.kotest.common.ExperimentalKotest
import io.kotest.core.config.AbstractProjectConfig
import io.kotest.core.extensions.ProjectExtension
import io.kotest.core.project.ProjectContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.debug.CoroutineInfo
import kotlinx.coroutines.debug.DebugProbes
import kotlinx.coroutines.debug.DebugProbes.dumpCoroutinesInfo
import kotlinx.coroutines.debug.DebugProbes.withDebugProbes
import library.telemetry.Telemetry

private val logger = Telemetry.logger("library.testing.JvmTestConfigurationKt")

/**
 * The project-wide test configuration.
 *
 * This configuration will be automatically picked up by Kotest via classpath scanning of [AbstractProjectConfig].
 */
@Suppress("unused")
object JvmTestConfiguration : CommonTestConfiguration() {
    override val coroutineDebugProbes = false // done in a differentiated way by DebugProbesTestExtension

    override val parallelism = Runtime.getRuntime().availableProcessors()

    override var dispatcherAffinity: Boolean? = false

    @OptIn(ExperimentalKotest::class)
    override val concurrentSpecs = Int.MAX_VALUE

    @OptIn(ExperimentalKotest::class)
    override val concurrentTests = Int.MAX_VALUE

    override fun extensions() = super.extensions() + listOf(
        CoroutineContextTestSpecExtension(Dispatchers.Default), // required for BlockHound detection
        DebugProbesTestExtension
    )

    /**
     * A test project extension enabling coroutine debug probes, automatically dumping stray coroutines.
     */
    object DebugProbesTestExtension : ProjectExtension {
        override suspend fun interceptProject(context: ProjectContext, callback: suspend (ProjectContext) -> Unit) =
            withDebugProbes {
                DebugProbes.enableCreationStackTraces = true
                try {
                    callback(context)
                } finally {
                    logStrayCoroutines()
                }
            }

        private fun logStrayCoroutines() {
            fun CoroutineInfo.isRelevant(): Boolean {
                // Ktor launches its Nonce generator in GlobalScope
                return !creationStackTrace.any { it.className == "io.ktor.util.NonceKt" }
            }

            val strayCoroutines = dumpCoroutinesInfo().filter { it.isRelevant() }

            if (strayCoroutines.isNotEmpty()) {
                logger.warning {
                    "${strayCoroutines.size} coroutines straying after end of tests:" +
                        strayCoroutines.joinToString(
                            separator = "\n\t",
                            prefix = "\n\t",
                            postfix = "\n"
                        ) {
                            buildString {
                                val indent = "\t\t"
                                appendLine("$it")
                                it.job?.let {
                                    appendLine(
                                        DebugProbes.jobToString(it).removeSuffix("\n").prependIndent(indent)
                                    )
                                }
                                appendLine("${indent}Creation stack trace:")
                                appendLine(
                                    it.creationStackTrace.joinToString(
                                        prefix = "$indent\t",
                                        separator = "\n$indent\t",
                                        limit = 20,
                                        truncated = "..."
                                    )
                                )
                            }
                        }
                }
            }
        }
    }
}

/**
 * The [TestVariant] selected for the current test runner invocation.
 */
actual val testVariant = when (System.getProperty("application.test.variant")) {
    "quick" -> TestVariant.QUICK
    "extended" -> TestVariant.EXTENDED
    "infinite" -> TestVariant.INFINITE
    else -> TestVariant.QUICK
}
