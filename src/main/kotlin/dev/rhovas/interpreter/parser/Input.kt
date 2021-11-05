package dev.rhovas.interpreter.parser

import java.util.*

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

    fun diagnostic(e: ParseException): String {
        val builder = StringBuilder("""
            |${source}:${e.range.line}:${e.range.column}-${e.range.column + e.range.length}
            |Error: ${e.summary}
        """.trimMargin())
        val context = TreeSet(compareBy(Range::line)).also {
            it.add(e.range)
        }
        val digits = context.last().line.toString().length
        context.forEach {
            val start = it.index - it.column
            val end = content.indexOfAny(charArrayOf('\n', '\r'), it.index + it.length)
            builder.append("""
                |
                | ${it.line.toString().padStart(digits)} | ${content.substring(start, end)}
            """.trimMargin())
            if (it.line == e.range.line) {
                builder.append("""
                    |
                    | ${" ".repeat(digits)} | ${" ".repeat(e.range.column)}${"^".repeat(e.range.length)}
                """.trimMargin())
            }
        }
        if (e.details.isNotEmpty()) {
            builder.append("""
                |
                |${e.details}
            """.trimMargin())
        }
        return builder.toString()
    }

}
