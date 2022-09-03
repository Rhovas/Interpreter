package dev.rhovas.interpreter.parser

data class Input(
    val source: String,
    val content: String,
) {

    data class Range(
        val index: Int,
        val line: Int,
        val column: Int,
        val length: Int,
    )

    fun diagnostic(summary: String, details: String, range: Range, context: List<Range>): String {
        val builder = StringBuilder()
            .append("${source}:${range.line}:${range.column}-${range.column + range.length}")
            .append("\nError: ${summary}")
        val ranges = mutableMapOf(range.line to range).also { map ->
            context.forEach { map.getOrPut(it.line) { it } }
        }.toList().sortedBy { it.first }.map { it.second }
        val digits = ranges.last().line.toString().length
        ranges.forEach {
            val start = it.index - it.column
            val end = content.indexOfAny(charArrayOf('\n', '\r'), it.index).takeIf { it != -1 } ?: content.length
            builder.append("\n ${it.line.toString().padStart(digits)} | ${content.substring(start, end)}")
            if (it.line == range.line) {
                builder.append("\n ${" ".repeat(digits)} | ${" ".repeat(range.column)}${"^".repeat(range.length)}")
            }
        }
        if (details.isNotEmpty()) {
            builder.append("\n${details}")
        }
        return builder.toString()
    }

}
