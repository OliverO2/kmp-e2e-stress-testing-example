package example.frontend.views

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshots.Snapshot
import example.frontend.service.FrontendService
import example.transport.TextLine
import library.core.simpleClassId

class TextLineViewModel(val textLine: TextLine) {
    var value by mutableStateOf(textLine.value)

    fun receiveUpdate(update: TextLine) {
        value = update.value
        textLine.value = update.value
    }

    fun applyChange(newValue: String) {
        value = newValue
        textLine.value = newValue
    }

    override fun toString(): String = Snapshot.withoutReadObservation {
        "$simpleClassId(id=${textLine.id}, value=$value)"
    }

    companion object {
        operator fun invoke(service: FrontendService, textLine: TextLine) = service.viewModel(textLine)
    }
}
