package library.core

val Any.simpleClassName: String get() = "${this::class.simpleName}"

@OptIn(ExperimentalStdlibApi::class)
val Any.simpleClassId: String get() = "$simpleClassName@{${hashCode().toHexString()}}"
