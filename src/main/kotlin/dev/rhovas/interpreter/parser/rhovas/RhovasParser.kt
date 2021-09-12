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
            peek("if") -> parseIfStatement()
            peek("match") -> parseMatchStatement()
            peek("for") -> parseForStatement()
            peek("while") -> parseWhileStatement()
            peek("try") -> parseTryStatement()
            peek("with") -> parseWithStatement()
            peek(RhovasTokenType.IDENTIFIER, ":") -> parseLabelStatement()
            peek("break") -> parseBreakStatement()
            peek("continue") -> parseContinueStatement()
            peek("return") -> parseReturnStatement()
            peek("throw") -> parseThrowStatement()
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

    private fun parseIfStatement(): RhovasAst.Statement.If {
        require(match("if"))
        require(match("(")) { "Expected opening parenthesis." }
        val condition = parseExpression()
        require(match(")")) { "Expected closing parenthesis." }
        val thenStatement = parseStatement()
        val elseStatement = if (match("else")) parseStatement() else null
        return RhovasAst.Statement.If(condition, thenStatement, elseStatement)
    }

    private fun parseMatchStatement(): RhovasAst.Statement.Match {
        require(match("match"))
        val argument = if (match("(")) {
            val argument = parseExpression()
            require(match(")")) { "Expected closing parenthesis." }
            argument
        } else null
        require(match("{")) { "Expected opening brace." }
        val cases = mutableListOf<Pair<RhovasAst.Expression, RhovasAst.Statement>>()
        var elseCase: Pair<RhovasAst.Expression?, RhovasAst.Statement>? = null
        while (!match("}")) {
            if (match("else")) {
                val condition = if (!peek(":")) parseExpression() else null
                require(match(":")) { "Expected colon." }
                val statement = parseStatement()
                elseCase = Pair(condition, statement)
                require(match("}")) { "Expected closing brace." }
                break
            } else {
                val condition = parseExpression()
                require(match(":")) { "Expected colon." }
                val statement = parseStatement()
                cases.add(Pair(condition, statement))
            }
        }
        return RhovasAst.Statement.Match(argument, cases, elseCase)
    }

    private fun parseForStatement(): RhovasAst.Statement.For {
        require(match("for"))
        require(match("(")) { "Expected opening parenthesis." }
        require(match("val")) { "Expected `val`." }
        require(match(RhovasTokenType.IDENTIFIER)) { "Expected identifier." }
        val name = tokens[-1]!!.literal
        require(match("in")) { "Expected `in`." }
        val iterable = parseExpression()
        require(match(")")) { "Expected closing parenthesis." }
        val body = parseStatement()
        return RhovasAst.Statement.For(name, iterable, body)
    }

    private fun parseWhileStatement(): RhovasAst.Statement.While {
        require(match("while"))
        require(match("(")) { "Expected opening parenthesis." }
        val condition = parseExpression()
        require(match(")")) { "Expected closing parenthesis." }
        val body = parseStatement()
        return RhovasAst.Statement.While(condition, body)
    }

    private fun parseTryStatement(): RhovasAst.Statement.Try {
        require(match("try"))
        val body = parseStatement()
        val catches = mutableListOf<RhovasAst.Statement.Try.Catch>()
        while (match("catch")) {
            require(match("(")) { "Expected opening parenthesis." }
            require(match("val")) { "Expected `val`." }
            require(match(RhovasTokenType.IDENTIFIER)) { "Expected identifier." }
            val name = tokens[-1]!!.literal
            require(match(")")) { "Expected closing parenthesis." }
            val catchBody = parseStatement()
            catches.add(RhovasAst.Statement.Try.Catch(name, catchBody))
        }
        val finallyStatement = if (match("finally")) parseStatement() else null
        return RhovasAst.Statement.Try(body, catches, finallyStatement)
    }

    private fun parseWithStatement(): RhovasAst.Statement.With {
        require(match("with"))
        require(match("(")) { "Expected opening parenthesis." }
        val name = if (match("val")) {
            require(match(RhovasTokenType.IDENTIFIER)) { "Expected identifier." }
            val name = tokens[-1]!!.literal
            require(match("=")) { "Expected equals." }
            name
        } else null
        val argument = parseExpression()
        require(match(")")) { "Expected closing parenthesis." }
        val body = parseStatement()
        return RhovasAst.Statement.With(name, argument, body)
    }

    private fun parseLabelStatement(): RhovasAst.Statement.Label {
        require(match(RhovasTokenType.IDENTIFIER))
        val label = tokens[-1]!!.literal
        require(match(":"))
        val statement = parseStatement()
        return RhovasAst.Statement.Label(label, statement)
    }

    private fun parseBreakStatement(): RhovasAst.Statement.Break {
        require(match("break"))
        val label = if (match(RhovasTokenType.IDENTIFIER)) tokens[-1]!!.literal else null
        require(match(";")) { "Expected semicolon." }
        return RhovasAst.Statement.Break(label)
    }

    private fun parseContinueStatement(): RhovasAst.Statement.Continue {
        require(match("continue"))
        val label = if (match(RhovasTokenType.IDENTIFIER)) tokens[-1]!!.literal else null
        require(match(";")) { "Expected semicolon." }
        return RhovasAst.Statement.Continue(label)
    }

    private fun parseReturnStatement(): RhovasAst.Statement.Return {
        require(match("return"))
        val value = if (!peek(";")) parseExpression() else null
        require(match(";")) { "Expected semicolon." }
        return RhovasAst.Statement.Return(value)
    }

    private fun parseThrowStatement(): RhovasAst.Statement.Throw {
        require(match("throw"))
        val exception = parseExpression()
        require(match(";")) { "Expected semicolon." }
        return RhovasAst.Statement.Throw(exception)
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
