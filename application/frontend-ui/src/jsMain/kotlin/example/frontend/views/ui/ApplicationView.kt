package example.frontend.views.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.material.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import example.frontend.service.BrowserFrontendService

@Composable
fun ApplicationView() {
    val service = remember { BrowserFrontendService() }

    LaunchedEffect(Unit) {
        service.launchIn(this)
    }

    MaterialTheme {
        Box(modifier = Modifier.padding(10.dp)) {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                HeaderView(service)

                PrimaryContentView(service)
            }
        }
    }
}
