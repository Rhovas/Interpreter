package dev.rhovas.interpreter.parser.dsl

import dev.rhovas.interpreter.parser.Input
import dev.rhovas.interpreter.parser.Parser
import dev.rhovas.interpreter.parser.rhovas.RhovasParser

class DslParser(input: Input) : Parser<DslTokenType>(DslLexer(input)) {

    override fun parse(rule: String): Any {
        return when (rule) {
            "source" -> parseSource()
            else -> throw AssertionError()
        }
    }

    /**
     * Parses a DSL using an indentation-based approach to identify the
     * start/end without grammar restrictions. This is either inline (without
     * newlines/interpolation) or multiline (with newlines/interpolation).
     *
     *  - `inline = "{" (text | "$")* "}"`
     *  - `multiline = "{" (indent[n] line indent[0]*)* indent[<n] "}"`
     *     - `line = "$" "{" rhovas-expression "}" | operator | text`
     */
    private fun parseSource(): DslAst.Source {
        require(match("{"))
        context.addLast(tokens[-1]!!.range)
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
                    context.addLast(tokens[-1]!!.range)
                    val parser = RhovasParser(lexer.input)
                    parser.lexer.state = lexer.state.let {
                        Pair(it.first.copy(index = it.first.index - 2, column = it.first.column - 2, length = 0), it.second)
                    }
                    val argument = parser.parse("interpolation")
                    lexer.state = parser.lexer.state.let {
                        Pair(it.first.copy(index = it.first.index - 1, column = it.first.column - 1, length = 0), it.second)
                    }
                    require(match("}"))
                    literals.add(builder.toString())
                    arguments.add(argument)
                    builder.clear()
                    context.removeLast()
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
        return DslAst.Source(literals, arguments).also {
            it.context = listOf(context.removeLast(), tokens[-1]!!.range)
        }
    }

}
