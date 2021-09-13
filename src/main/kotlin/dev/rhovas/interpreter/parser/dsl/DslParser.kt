package dev.rhovas.interpreter.parser.dsl

import dev.rhovas.interpreter.parser.ParseException
import dev.rhovas.interpreter.parser.Parser

class DslParser(input: String) : Parser<DslTokenType>(DslLexer(input)) {

    override fun parse(rule: String): Any {
        return when (rule) {
            "source" -> parseSource()
            else -> throw AssertionError()
        }.also { require(tokens[0] == null) { "Expected end of input." } }
    }

    private fun parseSource(): DslAst.Source {
        require(match("{"))
        val builder = StringBuilder()
        if (match(DslTokenType.INDENT)) {
            val indent = tokens[-1]!!.literal
            while (true) {
                if (match(DslTokenType.INDENT)) {
                    val literal = tokens[-1]!!.literal
                    if (literal.length < indent.length && !peek(DslTokenType.INDENT)) {
                        break
                    }
                    builder.append("\n").append(literal.removePrefix(indent))
                } else if (match("$", "{")) {
                    throw ParseException("TODO: Interpolation") //TODO
                } else {
                    require(tokens[0] != null) { "Expected token." }
                    tokens.advance()
                    builder.append(tokens[-1]!!.literal)
                }
            }
        } else {
            while (tokens[0] != null && !peek(listOf(DslTokenType.INDENT, "{", "}"))) {
                tokens.advance()
                builder.append(tokens[-1]!!.literal)
            }
        }
        require(match("}")) { "Expected closing brace." }
        return DslAst.Source(listOf(builder.toString()), listOf())
    }

}
