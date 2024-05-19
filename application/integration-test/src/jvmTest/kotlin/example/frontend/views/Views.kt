@file:Suppress("TestFunctionName")

package example.frontend.views

import androidx.compose.runtime.Composable
import androidx.compose.runtime.ComposeNode
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import example.frontend.service.FrontendService
import library.core.debugTrace

@Composable
fun FrontendView(service: () -> FrontendService, effectCoroutine: (suspend () -> Unit)? = null) {
    @Suppress("NAME_SHADOWING")
    val service = remember { service() }

    LaunchedEffect(Unit) {
        service.launchIn(this)
    }

    if (effectCoroutine != null) {
        LaunchedEffect(Unit) {
            effectCoroutine()
        }
    }

    PrimaryContentView(service)
}

@Composable
fun PrimaryContentView(service: FrontendService) {
    service.debugTrace?.log(null) { "recomposing PrimaryContentView" }

    for (textLineViewModel in service.viewModels) {
        TextLineView(service, textLineViewModel)
    }
}

@Composable
fun TextLineView(service: FrontendService, viewModel: TextLineViewModel) {
    service.debugTrace?.log(null) { "recomposing TextView ${viewModel.textLine.id}" }

    ComposeNode<ViewNode.Text, ViewNode.Applier>(
        factory = { ViewNode.Text(service, viewModel) },
        update = {
            set(viewModel.value) { newValue ->
                content = newValue
            }
        }
    )
}
