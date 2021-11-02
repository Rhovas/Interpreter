package dev.rhovas.interpreter.parser.dsl

import dev.rhovas.interpreter.parser.Parser
import dev.rhovas.interpreter.parser.rhovas.RhovasParser

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
        val literals = mutableListOf<String>()
        val arguments = mutableListOf<Any>()
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
                    val parser = RhovasParser(lexer.input)
                    parser.lexer.state = lexer.state - 2
                    val argument = parser.parse("interpolation")
                    lexer.state = parser.lexer.state - 1
                    require(match("}"))
                    literals.add(builder.toString())
                    arguments.add(argument)
                    builder.clear()
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
        literals.add(builder.toString())
        require(match("}")) { "Expected closing brace." }
        return DslAst.Source(literals, arguments)
    }

}
