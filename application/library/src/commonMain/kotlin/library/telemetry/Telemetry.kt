package library.telemetry

object Telemetry {
    fun logger(name: String, level: LogLevel = LogLevel.DEBUG) = Logger(name, level)
}
