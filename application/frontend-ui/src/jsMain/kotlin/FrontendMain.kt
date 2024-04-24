import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.window.CanvasBasedWindow
import example.frontend.views.ui.ApplicationView
import org.jetbrains.skiko.wasm.onWasmReady

fun main() {
    onWasmReady {
        @OptIn(ExperimentalComposeUiApi::class)
        CanvasBasedWindow("Web/JS Frontend (Canvas)") {
            ApplicationView()
        }
    }
}
