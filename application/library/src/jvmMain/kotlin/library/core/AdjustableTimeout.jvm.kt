package library.core

import kotlinx.coroutines.asContextElement

actual val adjustableTimeoutFactor: Double
    get() = threadLocalFactor.get()

fun adjustableTimeoutFactorContextElement(value: Double) = threadLocalFactor.asContextElement(value)

private val threadLocalFactor = ThreadLocal.withInitial { 1.0 }
