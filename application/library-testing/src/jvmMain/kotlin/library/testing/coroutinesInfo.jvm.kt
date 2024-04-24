package library.testing

import kotlinx.coroutines.CoroutineName
import kotlinx.coroutines.debug.CoroutineInfo
import kotlinx.coroutines.debug.DebugProbes
import kotlinx.coroutines.debug.DebugProbes.dumpCoroutinesInfo

fun coroutinesInfo(isRelevant: CoroutineInfo.() -> Boolean): String {
    val allCoroutines = dumpCoroutinesInfo()
    val relevantCoroutines = allCoroutines.filter { it.isRelevant() }
    val headline = (allCoroutines.size - relevantCoroutines.size).let { exclusionCount ->
        if (exclusionCount == 0) {
            "${allCoroutines.size} coroutines present"
        } else {
            "${relevantCoroutines.size} relevant coroutines present ($exclusionCount excluded)"
        }
    }

    return if (relevantCoroutines.isNotEmpty()) {
        "$headline:" +
            relevantCoroutines.joinToString(
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
    } else {
        "(no coroutines info)\n"
    }
}

actual fun coroutinesInfo(): String = coroutinesInfo {
    val name = context[CoroutineName]?.name
    name == null || listOf("ws-", "raw-ws").none { name.startsWith(it) }
}
