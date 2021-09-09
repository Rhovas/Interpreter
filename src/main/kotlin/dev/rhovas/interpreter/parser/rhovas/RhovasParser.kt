package dev.rhovas.interpreter.parser.rhovas

import dev.rhovas.interpreter.parser.ParseException
import dev.rhovas.interpreter.parser.Parser

class RhovasParser(input: String) : Parser<RhovasTokenType>(RhovasLexer(input)) {

    override fun parse(rule: String): RhovasAst {
        return when (rule) {
            "expression" -> parseExpression()
            else -> throw AssertionError()
        }.also { require(tokens[0] == null) { "Expected end of input." } }
    }

    fun parseExpression(): RhovasAst.Expression {
        return parseLogicalOrExpression()
    }

    private fun parseLogicalOrExpression(): RhovasAst.Expression {
        return parseBinaryExpression(::parseLogicalAndExpression, "||")
    }

    private fun parseLogicalAndExpression(): RhovasAst.Expression {
        return parseBinaryExpression(::parseEqualityExpression, "&&")
    }

    private fun parseEqualityExpression(): RhovasAst.Expression {
        return parseBinaryExpression(::parseComparisonExpression, "==", "!=", "===", "!==")
    }

    private fun parseComparisonExpression(): RhovasAst.Expression {
        return parseBinaryExpression(::parseAdditiveExpression, "<", ">", "<=", ">=")
    }

    private fun parseAdditiveExpression(): RhovasAst.Expression {
        return parseBinaryExpression(::parseMultiplicativeExpression, "+", "-")
    }

    private fun parseMultiplicativeExpression(): RhovasAst.Expression {
        return parseBinaryExpression(::parseUnaryExpression, "*", "/")
    }

    private fun parseBinaryExpression(parser: () -> RhovasAst.Expression, vararg operators: String): RhovasAst.Expression {
        var expression = parser()
        while (true) {
            val operator = operators.sorted().lastOrNull { o ->
                match(*o.toCharArray().map { it.toString() }.toTypedArray())
            } ?: break
            val right = parser()
            expression = RhovasAst.Expression.Binary(operator, expression, right)
        }
        return expression
    }

    private fun parseUnaryExpression(): RhovasAst.Expression {
        return if (match(listOf("-", "!"))) {
            val operator = tokens[-1]!!.literal
            val expression = parseUnaryExpression()
            RhovasAst.Expression.Unary(operator, expression)
        } else {
            parsePrimaryExpression()
        }
    }

    private fun parsePrimaryExpression(): RhovasAst.Expression {
        return when {
            match("null") -> RhovasAst.Expression.Literal(null)
            match(listOf("true", "false")) -> RhovasAst.Expression.Literal(tokens[-1]!!.literal.toBooleanStrict())
            match(RhovasTokenType.INTEGER) -> RhovasAst.Expression.Literal(tokens[-1]!!.literal.toBigInteger())
            match(RhovasTokenType.DECIMAL) -> RhovasAst.Expression.Literal(tokens[-1]!!.literal.toBigDecimal())
            match(RhovasTokenType.STRING) -> {
                //TODO: Escapes
                RhovasAst.Expression.Literal(tokens[-1]!!.literal.removeSurrounding("\""))
            }
            match(":", RhovasTokenType.IDENTIFIER) -> RhovasAst.Expression.Literal(RhovasAst.Atom(tokens[-1]!!.literal))
            match(RhovasTokenType.IDENTIFIER) -> {
                val name = tokens[-1]!!.literal
                if (match("(")) {
                    val arguments = mutableListOf<RhovasAst.Expression>()
                    while (!match(")")) {
                        arguments.add(parseExpression())
                        require(peek(")") || match(",")) { "Expected closing parenthesis or comma." }
                    }
                    RhovasAst.Expression.Function(name, arguments)
                } else {
                    RhovasAst.Expression.Access(name)
                }
            }
            else -> throw ParseException("Expected expression.")
        }
    }

}
