package top.wang3.slf4k.compiler

internal object Slf4kDiagnosticMessages {
    private const val SLF4J_API_COORDINATE = "org.slf4j:slf4j-api"

    fun missingSlf4jApiForClass(className: String, missingSymbols: List<String>): String {
        val missing = missingSymbols.joinToString(" and ")
        return "slf4k cannot generate logger for $className because $missing is missing. " +
            "Add $SLF4J_API_COORDINATE to the compile classpath."
    }

    fun missingSlf4jApiInIr(missingSymbol: String): String =
        "slf4k cannot initialize generated loggers because $missingSymbol is missing. " +
            "Add $SLF4J_API_COORDINATE to the compile classpath."
}
