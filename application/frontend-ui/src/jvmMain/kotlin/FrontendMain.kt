import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.WindowState
import androidx.compose.ui.window.singleWindowApplication
import example.frontend.views.ui.ApplicationView

fun main() {
    singleWindowApplication(
        title = "Desktop Frontend",
        state = WindowState(width = 400.dp, height = 800.dp)
    ) {
        ApplicationView()
    }
}
