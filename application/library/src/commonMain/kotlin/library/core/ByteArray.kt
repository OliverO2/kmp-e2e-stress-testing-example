package library.core

/**
 * returns the [ByteArray] contents as a string of hexadecimal bytes.
 */
fun ByteArray.toHexString() = asUByteArray().joinToString("") { it.toString(16).padStart(2, '0') }
