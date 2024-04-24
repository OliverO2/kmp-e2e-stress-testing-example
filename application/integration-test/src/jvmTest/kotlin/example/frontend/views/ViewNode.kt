package example.frontend.views

import androidx.compose.runtime.AbstractApplier
import example.frontend.service.FrontendService
import library.core.GuardedBy
import library.core.debugString
import library.core.debugTrace
import library.core.simpleClassId

/**
 * A view node is a node in a composition's UI object tree. It is generated by a Composable.
 *
 * NOTE: During testing, elements and their contents are typically checked outside the Composer coroutine,
 * so mutable data may be modified and accessed on different threads. This means that
 * - mutable containers (lists, etc.) must be guarded by `Type.sync { ... }`,
 * - mutable properties (strings, etc.) must be designated as @Volatile.
 */
open class ViewNode protected constructor(@Volatile open var name: String = "?") {
    @GuardedBy("itself") // list element access
    protected val children = mutableListOf<ViewNode>()

    override fun toString(): String = "{ \"$name\": ${children.debugString()} }"

    class Root : ViewNode("Composition") {
        fun child(index: Int) = children.sync { this.getOrNull(index) }

        override fun toString(): String = children.sync {
            "{ \"$name\": ${debugString()} }"
        }
    }

    class Text(
        private val service: FrontendService,
        private val viewModel: TextLineViewModel
    ) : ViewNode("Text") {
        @Volatile
        var content: String = ""
            // get() {
            //     service.debugTrace?.log(null) { "$description.get: \"$field\"" }
            //     return field
            // }
            set(value) {
                service.debugTrace?.log(null) { "$description.set: \"$field\" -> \"$value\"" }
                field = value
            }

        fun update(value: String) {
            service.processViewChange(viewModel, value)
            viewModel.value = value
        }

        private val description get() = "$simpleClassId(service=$service, textLineId=${viewModel.textLine.id})"

        override fun toString(): String = "\"$content\""
    }

    class Applier(root: Root) : AbstractApplier<ViewNode>(root) {
        override fun insertTopDown(index: Int, instance: ViewNode) {} // ignored, building tree bottom-up
        override fun insertBottomUp(index: Int, instance: ViewNode) = current.children.sync { add(index, instance) }
        override fun remove(index: Int, count: Int) = current.children.sync { remove(index, count) }
        override fun move(from: Int, to: Int, count: Int) = current.children.sync { move(from, to, count) }
        override fun onClear() = root.children.sync { clear() }
    }
}

private fun <Type : Any, Result> Type.sync(block: Type.() -> Result): Result = synchronized(this) {
    block()
}