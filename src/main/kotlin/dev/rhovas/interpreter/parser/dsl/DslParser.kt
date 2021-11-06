package dev.rhovas.interpreter.parser.dsl

import dev.rhovas.interpreter.parser.Input
import dev.rhovas.interpreter.parser.Parser
import dev.rhovas.interpreter.parser.rhovas.RhovasParser

class DslParser(input: Input) : Parser<DslTokenType>(DslLexer(input)) {

    override fun parse(rule: String): Any {
        return when (rule) {
            "source" -> parseSource()
            else -> throw AssertionError()
        }.also { require(tokens[0] == null) { error("Expected end of input.") } }
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
                    parser.lexer.state = lexer.state.let { it.copy(index = it.index - 2, column = it.column - 2, length = 0) }
                    val argument = parser.parse("interpolation")
                    lexer.state = parser.lexer.state.let { it.copy(index = it.index - 1, column = it.column - 1, length = 0) }
                    require(match("}"))
                    literals.add(builder.toString())
                    arguments.add(argument)
                    builder.clear()
                } else {
                    require(tokens[0] != null) { error(
                        "Expected input.",
                        "A DSL expression is terminated by a closing brace `}` (if defined inline) or a closing brace `}` at a lower indentation than the start (if defined multiline).",
                    ) }
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
        require(match("}")) { error(
            "Expected closing brace.",
            "A DSL expression requires braces around the source, as in `#dsl \${ source }`.",
        ) }
        return DslAst.Source(literals, arguments)
    }

}
