package example.frontend.views.ui

import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.absolutePadding
import androidx.compose.material.OutlinedTextField
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.FirstBaseline
import androidx.compose.ui.unit.dp
import example.frontend.service.FrontendService
import example.frontend.views.TextLineViewModel

@Composable
fun HeaderView(service: FrontendService) {
    service.clientId?.let {
        Text("This is client #$it")
    }
}

@Composable
fun PrimaryContentView(service: FrontendService) {
    for (element in service.viewModels) {
        Row {
            Text(
                "id=${element.textLine.id}:",
                modifier = Modifier.absolutePadding(right = 12.dp).alignBy(FirstBaseline)
            )
            TextLineView(service, element, modifier = Modifier.alignBy(FirstBaseline))
        }
    }
}

@Composable
private fun TextLineView(service: FrontendService, viewModel: TextLineViewModel, modifier: Modifier = Modifier) {
    OutlinedTextField(
        value = viewModel.value,
        onValueChange = { newValue ->
            service.processViewChange(viewModel, newValue)
        },
        modifier = modifier
    )
}
