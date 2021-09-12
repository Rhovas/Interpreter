package dev.rhovas.interpreter.parser.rhovas

import dev.rhovas.interpreter.parser.ParseException
import dev.rhovas.interpreter.parser.Parser

class RhovasParser(input: String) : Parser<RhovasTokenType>(RhovasLexer(input)) {

    override fun parse(rule: String): RhovasAst {
        return when (rule) {
            "statement" -> parseStatement()
            "expression" -> parseExpression()
            else -> throw AssertionError()
        }.also { require(tokens[0] == null) { "Expected end of input." } }
    }

    private fun parseStatement(): RhovasAst.Statement {
        return when {
            peek("{") -> parseBlockStatement()
            peek(listOf("val", "var")) -> parseDeclarationStatement()
            else -> {
                val expression = parseExpression()
                val statement = if (match("=")) {
                    val value = parseExpression()
                    RhovasAst.Statement.Assignment(expression, value)
                } else {
                    RhovasAst.Statement.Expression(expression)
                }
                require(match(";")) { "Expected semicolon." }
                statement
            }
        }
    }

    private fun parseBlockStatement(): RhovasAst.Statement.Block {
        require(match("{"))
        val statements = mutableListOf<RhovasAst.Statement>()
        while (!match("}")) {
            statements.add(parseStatement())
        }
        return RhovasAst.Statement.Block(statements)
    }

    private fun parseDeclarationStatement(): RhovasAst.Statement.Declaration {
        require(match(listOf("val", "var")))
        val mutable = tokens[-1]!!.literal == "var"
        require(match(RhovasTokenType.IDENTIFIER)) { "Expected identifier." }
        val name = tokens[-1]!!.literal
        val value = if (match("=")) parseExpression() else null
        require(match(";")) { "Expected semicolon." }
        return RhovasAst.Statement.Declaration(mutable, name, value)
    }

    private fun parseExpression(): RhovasAst.Expression {
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
            parseSecondaryExpression()
        }
    }

    private fun parseSecondaryExpression(): RhovasAst.Expression {
        var expression = parsePrimaryExpression()
        while (true) {
            expression = when {
                match(".", RhovasTokenType.IDENTIFIER) -> {
                    val name = tokens[-1]!!.literal
                    if (match("(")) {
                        val arguments = mutableListOf<RhovasAst.Expression>()
                        while (!match(")")) {
                            arguments.add(parseExpression())
                            require(peek(")") || match(",")) { "Expected closing parenthesis or comma." }
                        }
                        RhovasAst.Expression.Function(expression, name, arguments)
                    } else {
                        RhovasAst.Expression.Access(expression, name)
                    }
                }
                match("[") -> {
                    val arguments = mutableListOf<RhovasAst.Expression>()
                    while (!match("]")) {
                        arguments.add(parseExpression())
                        require(peek("]") || match(",")) { "Expected closing bracket or comma." }
                    }
                    RhovasAst.Expression.Index(expression, arguments)
                }
                else -> return expression
            }
        }
    }

    private fun parsePrimaryExpression(): RhovasAst.Expression {
        return when {
            match("null") -> RhovasAst.Expression.Literal(null)
            match(listOf("true", "false")) -> RhovasAst.Expression.Literal(tokens[-1]!!.literal.toBooleanStrict())
            match(RhovasTokenType.INTEGER) ||
            match(RhovasTokenType.DECIMAL) ||
            match(RhovasTokenType.STRING) -> RhovasAst.Expression.Literal(tokens[-1]!!.value)
            match(":", RhovasTokenType.IDENTIFIER) -> RhovasAst.Expression.Literal(RhovasAst.Atom(tokens[-1]!!.literal))
            match("[") -> {
                val elements = mutableListOf<RhovasAst.Expression>()
                while (!match("]")) {
                    elements.add(parseExpression())
                    require(peek("]") || match(",")) { "Expected closing parenthesis or comma." }
                }
                RhovasAst.Expression.Literal(elements)
            }
            match("{") -> {
                val properties = mutableMapOf<String, RhovasAst.Expression>()
                while (!match("}")) {
                    require(match(RhovasTokenType.IDENTIFIER)) { "Expected property key." }
                    val key = tokens[-1]!!.literal
                    properties[key] = if (match(":")) parseExpression() else RhovasAst.Expression.Access(null, key)
                    require(peek("}") || match(",")) { "Expected closing parenthesis or comma." }
                }
                RhovasAst.Expression.Literal(properties)
            }
            match("(") -> {
                val expression = parseExpression()
                require(match(")")) { "Expected closing parenthesis." }
                RhovasAst.Expression.Group(expression)
            }
            match(RhovasTokenType.IDENTIFIER) -> {
                val name = tokens[-1]!!.literal
                if (match("(")) {
                    val arguments = mutableListOf<RhovasAst.Expression>()
                    while (!match(")")) {
                        arguments.add(parseExpression())
                        require(peek(")") || match(",")) { "Expected closing parenthesis or comma." }
                    }
                    RhovasAst.Expression.Function(null, name, arguments)
                } else {
                    RhovasAst.Expression.Access(null, name)
                }
            }
            match("#", RhovasTokenType.IDENTIFIER) -> {
                val name = tokens[-1]!!.literal
                if (match("(")) {
                    val arguments = mutableListOf<RhovasAst.Expression>()
                    while (!match(")")) {
                        arguments.add(parseExpression())
                        require(peek(")") || match(",")) { "Expected closing parenthesis or comma." }
                    }
                    RhovasAst.Expression.Macro(name, arguments)
                } else {
                    throw ParseException("TODO: Syntax macros") //TODO
                }
            }
            else -> throw ParseException("Expected expression.")
        }
    }

}
