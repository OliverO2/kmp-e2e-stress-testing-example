package library.core

actual val adjustableTimeoutFactor: Double
    get() = globalFactor

fun setAdjustableTimeoutFactor(value: Double) {
    globalFactor = value
}

private var globalFactor: Double = 1.0
