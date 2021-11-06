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
        val builder = StringBuilder()
            .append("${source}:${e.range.line}:${e.range.column}-${e.range.column + e.range.length}")
            .append("\nError: ${e.summary}")
        val context = TreeSet(compareBy(Range::line)).also {
            it.add(e.range)
        }
        val digits = context.last().line.toString().length
        context.forEach {
            val start = it.index - it.column
            val end = content.indexOfAny(charArrayOf('\n', '\r'), it.index).takeIf { it != -1 } ?: content.length
            println(content)
            println(content.substring(start, end))
            builder.append("\n ${it.line.toString().padStart(digits)} | ${content.substring(start, end)}")
            if (it.line == e.range.line) {
                builder.append("\n ${" ".repeat(digits)} | ${" ".repeat(e.range.column)}${"^".repeat(e.range.length)}")
            }
        }
        if (e.details.isNotEmpty()) {
            builder.append("\n${e.details}")
        }
        return builder.toString()
    }

}
