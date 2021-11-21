package dev.rhovas.interpreter.parser.rhovas

import dev.rhovas.interpreter.parser.Input
import dev.rhovas.interpreter.parser.Parser
import dev.rhovas.interpreter.parser.dsl.DslParser

class RhovasParser(input: Input) : Parser<RhovasTokenType>(RhovasLexer(input)) {

    override fun parse(rule: String): RhovasAst {
        return when (rule) {
            "source" -> parseSource()
            "statement" -> parseStatement()
            "expression" -> parseExpression()
            "pattern" -> parsePattern()
            "interpolation" -> parseInterpolation()
            else -> throw AssertionError()
        }.also { require(rule == "interpolation" || tokens[0] == null) {
            error(
                "Expected end of input.",
                "Parsing for the `${rule}` rule completed without consuming all input. This is normally an implementation problem.",
            )
        } }
    }

    private fun parseSource(): RhovasAst.Source {
        val statements = mutableListOf<RhovasAst.Statement>()
        while (tokens[0] != null) {
            statements.add(parseStatement())
        }
        return RhovasAst.Source(statements)
    }

    private fun parseStatement(): RhovasAst.Statement {
        return when {
            peek("{") -> parseBlockStatement()
            peek("func") -> parseFunctionStatement()
            peek(listOf("val", "var")) -> parseDeclarationStatement()
            peek("if") -> parseIfStatement()
            peek("match") -> parseMatchStatement()
            peek("for") -> parseForStatement()
            peek("while") -> parseWhileStatement()
            peek("try") -> parseTryStatement()
            peek("with") -> parseWithStatement()
            peek("break") -> parseBreakStatement()
            peek("continue") -> parseContinueStatement()
            peek("return") -> parseReturnStatement()
            peek("throw") -> parseThrowStatement()
            peek("assert") -> parseAssertStatement()
            peek("require") -> parseRequireStatement()
            peek("ensure") -> parseEnsureStatement()
            peek(RhovasTokenType.IDENTIFIER, ":") -> parseLabelStatement()
            else -> {
                val expression = parseExpression()
                if (match("=")) {
                    val value = parseExpression()
                    requireSemicolon { "An assignment statement must be followed by a semicolon, as in `name = value;`." }
                    RhovasAst.Statement.Assignment(expression, value)
                } else {
                    requireSemicolon { "An expression statement must be followed by a semicolon, as in `expression;`" }
                    RhovasAst.Statement.Expression(expression)
                }
            }
        }
    }

    private fun parseBlockStatement(): RhovasAst.Statement.Block {
        require(match("{"))
        context.addLast(tokens[-1]!!.range)
        val statements = mutableListOf<RhovasAst.Statement>()
        while (!match("}")) {
            statements.add(parseStatement())
        }
        context.removeLast()
        return RhovasAst.Statement.Block(statements)
    }

    private fun parseFunctionStatement(): RhovasAst.Statement.Function {
        require(match("func"))
        context.addLast(tokens[-1]!!.range)
        val name = parseIdentifier { "A function declaration requires a name after `func`, as in `func name() { ... }`." }
        val parameters = mutableListOf<Pair<String, RhovasAst.Type?>>()
        require(match("(")) { error(
            "Expected opening parenthesis.",
            "A function declaration requires parenthesis after the name, as in `func name() { ... }` or `func name(x, y, z) { ... }`.",
        ) }
        while (!match(")")) {
            val name = parseIdentifier { "A function parameter requires a name, as in `func f(name: Type) { ... }`." }
            context.addLast(tokens[-1]!!.range)
            val type = if (match(":")) parseType() else null
            parameters.add(Pair(name, type))
            require(peek(")") || match(",")) { error(
                "Expected closing parenthesis or comma.",
                "A function parameter must be followed by a closing parenthesis `)` or comma `,`, as in `func name() { ... }` or `func name(x, y, z) { ... }`.",
            ) }
            context.removeLast()
        }
        val returns = if (match(":")) parseType() else null
        val body = parseStatement()
        context.removeLast()
        return RhovasAst.Statement.Function(name, parameters, returns, body)
    }

    private fun parseDeclarationStatement(): RhovasAst.Statement.Declaration {
        require(match(listOf("val", "var")))
        context.addLast(tokens[-1]!!.range)
        val mutable = tokens[-1]!!.literal == "var"
        val name = parseIdentifier { "A variable declaration requires a name after `val`/`var`, as in `val name = value;` or `var name: Type;`." }
        val type = if (match(":")) parseType() else null
        val value = if (match("=")) parseExpression() else null
        requireSemicolon { "A variable declaration must be followed by a semicolon, as in `val name = value;` or `var name: Type;`." }
        context.removeLast()
        return RhovasAst.Statement.Declaration(mutable, name, type, value)
    }

    private fun parseIfStatement(): RhovasAst.Statement.If {
        require(match("if"))
        context.addLast(tokens[-1]!!.range)
        require(match("(")) { error(
            "Expected opening parenthesis.",
            "An if statement requires parenthesis around the condition, as in `if (condition) { ... }`.",
        ) }
        val condition = parseExpression()
        require(match(")")) { error(
            "Expected closing parenthesis.",
            "An if statement condition must be followed by a closing parenthesis, as in `if (condition) { ... }`.",
        ) }
        val thenStatement = parseStatement()
        val elseStatement = if (match("else")) {
            context.addLast(tokens[-1]!!.range)
            val elseStatement = parseStatement()
            context.removeLast()
            elseStatement
        } else null
        context.removeLast()
        return RhovasAst.Statement.If(condition, thenStatement, elseStatement)
    }

    private fun parseMatchStatement(): RhovasAst.Statement.Match {
        require(peek("match"))
        return when {
            peek("match", "{") -> parseConditionalMatch()
            peek("match", "(") -> parseStructuralMatch()
            else -> throw error(
                "Expected opening brace or parenthesis.",
                "A match statement must be followed by an opening brace `{` (conditional match) or an opening parenthesis `(` (structural match), as in `match { condition: ... }` or `match (argument) { pattern: ... }`.",
            )
        }
    }

    private fun parseConditionalMatch(): RhovasAst.Statement.Match.Conditional {
        require(match("match", "{"))
        context.addLast(tokens[-2]!!.range)
        val cases = mutableListOf<Pair<RhovasAst.Expression, RhovasAst.Statement>>()
        var elseCase: Pair<RhovasAst.Expression?, RhovasAst.Statement>? = null
        while (!match("}")) {
            if (match("else")) {
                context.addLast(tokens[-1]!!.range)
                val condition = if (!peek(":")) parseExpression() else null
                require(match(":")) { error(
                    "Expected colon.",
                    "A match condition must be followed by a colon `:`, as in `match { condition: ... }`.",
                ) }
                val statement = parseStatement()
                elseCase = Pair(condition, statement)
                require(peek("}")) { error(
                    "Expected closing brace.",
                    "A match else condition must be the last condition and thus must be followed by a closing brace `}`, as in `match { c1: ... else c2: ... }`.",
                ) }
                context.removeLast()
            } else {
                context.addLast((tokens[0] ?: tokens[-1]!!).range)
                val condition = parseExpression()
                require(match(":")) { error(
                    "Expected colon.",
                    "A match condition must be followed by a colon `:`, as in `match { condition: ... }`.",
                ) }
                val statement = parseStatement()
                cases.add(Pair(condition, statement))
                context.removeLast()
            }
        }
        context.removeLast()
        return RhovasAst.Statement.Match.Conditional(cases, elseCase)
    }

    private fun parseStructuralMatch(): RhovasAst.Statement.Match.Structural {
        require(match("match", "("))
        context.addLast(tokens[-2]!!.range)
        val argument = parseExpression()
        require(match(")")) { error(
            "Expected closing parenthesis.",
            "A structural match argument must be followed by a closing parenthesis `)`, as in `match (argument) { ... }`.",
        ) }
        require(match("{")) { error(
            "Expected opening brace.",
            "A structural match argument must be followed by an opening brace `{`, as in `match (argument) { ... }`.",
        ) }
        val cases = mutableListOf<Pair<RhovasAst.Pattern, RhovasAst.Statement>>()
        var elseCase: Pair<RhovasAst.Pattern?, RhovasAst.Statement>? = null
        while (!match("}")) {
            if (match("else")) {
                context.addLast(tokens[-1]!!.range)
                val pattern = if (!peek(":")) parsePattern() else null
                require(match(":")) { error(
                    "Expected colon.",
                    "A match pattern must be followed by a colon `:`, as in `match (argument) { condition: ... }`.",
                ) }
                val statement = parseStatement()
                elseCase = Pair(pattern, statement)
                require(peek("}")) { error(
                    "Expected closing brace.",
                    "A match else pattern must be the last pattern and thus must be followed by a closing brace `}`, as in `match { p1: ... else p2: ... }`.",
                ) }
                context.removeLast()
            } else {
                context.addLast((tokens[0] ?: tokens[-1])!!.range)
                val condition = parsePattern()
                require(match(":")) { error(
                    "Expected colon.",
                    "A match pattern must be followed by a colon `:`, as in `match (argument) { condition: ... }`.",
                ) }
                val statement = parseStatement()
                cases.add(Pair(condition, statement))
                context.removeLast()
            }
        }
        return RhovasAst.Statement.Match.Structural(argument, cases, elseCase)
    }

    private fun parseForStatement(): RhovasAst.Statement.For {
        require(match("for"))
        context.addLast(tokens[-1]!!.range)
        require(match("(")) { error(
            "Expected opening parenthesis.",
            "A for loop requires parenthesis around the argument, as in `for (val name in iterable) { ... }`.",
        ) }
        require(match("val")) { error(
            "Expected `val`.",
            "A for loop variable requires `val`, as in `for (val name in iterable) { ... }`.",
        ) }
        val name = parseIdentifier { "A for loop variable requires a name, as in `for (val name in iterable) { ... }`." }
        require(match("in")) { error(
            "Expected `in`.",
            "A for loop variable must be followed by `in`, as in `for (val name in iterable) { ... }`.",
        ) }
        val iterable = parseExpression()
        require(match(")")) { error(
            "Expected closing parenthesis.",
            "A for loop requires parenthesis around the argument, as in `for (val name in iterable) { ... }`.",
        ) }
        val body = parseStatement()
        context.removeLast()
        return RhovasAst.Statement.For(name, iterable, body)
    }

    private fun parseWhileStatement(): RhovasAst.Statement.While {
        require(match("while"))
        context.addLast(tokens[-1]!!.range)
        require(match("(")) { error(
            "Expected opening parenthesis.",
            "A while loop requires parenthesis around the condition, as in `while (condition) { ... }`.",
        ) }
        val condition = parseExpression()
        require(match(")")) { error(
            "Expected closing parenthesis.",
            "A while loop requires parenthesis around the condition, as in `while (condition) { ... }`.",
        ) }
        val body = parseStatement()
        context.removeLast()
        return RhovasAst.Statement.While(condition, body)
    }

    private fun parseTryStatement(): RhovasAst.Statement.Try {
        require(match("try"))
        context.addLast(tokens[-1]!!.range)
        val body = parseStatement()
        val catches = mutableListOf<RhovasAst.Statement.Try.Catch>()
        while (match("catch")) {
            context.addLast(tokens[-1]!!.range)
            require(match("(")) { error(
                "Expected opening parenthesis.",
                "A catch block requires parenthesis around the argument, as in `try { ... } catch (val name) { ... }`.",
            ) }
            require(match("val")) { error(
                "Expected `val`.",
                "A catch block variable requires `val`, as in `try { ... } catch (val name) { ... }`.",
            ) }
            val name = parseIdentifier { "A catch block variable requires a name, as in `try { ... } catch (val name) { ... }`." }
            require(match(")")) { error(
                "Expected closing parenthesis.",
                "A catch block requires parenthesis around the argument, as in `try { ... } catch (val name) { ... }`.",
            ) }
            val catchBody = parseStatement()
            catches.add(RhovasAst.Statement.Try.Catch(name, catchBody))
            context.removeLast()
        }
        val finallyStatement = if (match("finally")) {
            context.addLast(tokens[-1]!!.range)
            val finallyStatement = parseStatement()
            context.removeLast()
            finallyStatement
        } else null
        context.removeLast()
        return RhovasAst.Statement.Try(body, catches, finallyStatement)
    }

    private fun parseWithStatement(): RhovasAst.Statement.With {
        require(match("with"))
        context.addLast(tokens[-1]!!.range)
        require(match("(")) { error(
            "Expected opening parenthesis.",
            "A with statement requires parenthesis around the argument, as in `with (argument) { ... }` or `with (val name = value) { ... }`.",
        ) }
        val name = if (match("val")) {
            val name = parseIdentifier { "A with statement variable requires a name, as in `with (val name = value) { ... }`." }
            require(match("=")) { error(
                "Expected equals.",
                "A with statement argument must be followed by equals `=`, as in `with (val name = value) { ... }`.",
            ) }
            name
        } else null
        val argument = parseExpression()
        require(match(")")) { error(
            "Expected closing parenthesis.",
            "A with statement requires parenthesis around the argument, as in `with (argument) { ... }` or `with (val name = value) { ... }`.",
        ) }
        val body = parseStatement()
        context.removeLast()
        return RhovasAst.Statement.With(name, argument, body)
    }

    private fun parseLabelStatement(): RhovasAst.Statement.Label {
        require(match(RhovasTokenType.IDENTIFIER))
        context.addLast(tokens[-1]!!.range)
        val label = tokens[-1]!!.literal
        require(match(":")) { error(
            "Expected colon.",
            "A label statement requires a label after the colon, as in `label: statement`.",
        ) }
        val statement = parseStatement()
        context.removeLast()
        return RhovasAst.Statement.Label(label, statement)
    }

    private fun parseBreakStatement(): RhovasAst.Statement.Break {
        require(match("break"))
        context.addLast(tokens[-1]!!.range)
        val label = if (match(RhovasTokenType.IDENTIFIER)) tokens[-1]!!.literal else null
        requireSemicolon { "A break statement must be followed by a semicolon, as in `break;` or `break label;`." }
        context.removeLast()
        return RhovasAst.Statement.Break(label)
    }

    private fun parseContinueStatement(): RhovasAst.Statement.Continue {
        require(match("continue"))
        context.addLast(tokens[-1]!!.range)
        val label = if (match(RhovasTokenType.IDENTIFIER)) tokens[-1]!!.literal else null
        requireSemicolon { "A continue statement must be followed by a semicolon, as in `continue;` or `continue label;`." }
        context.removeLast()
        return RhovasAst.Statement.Continue(label)
    }

    private fun parseReturnStatement(): RhovasAst.Statement.Return {
        require(match("return"))
        context.addLast(tokens[-1]!!.range)
        val value = if (!peek(";")) parseExpression() else null
        requireSemicolon { "A return statement must be followed by a semicolon, as in `return;` or `return value;`." }
        context.removeLast()
        return RhovasAst.Statement.Return(value)
    }

    private fun parseThrowStatement(): RhovasAst.Statement.Throw {
        require(match("throw"))
        context.addLast(tokens[-1]!!.range)
        val exception = parseExpression()
        requireSemicolon { "A throw statement must be followed by a semicolon, as in `throw exception;`." }
        context.removeLast()
        return RhovasAst.Statement.Throw(exception)
    }

    private fun parseAssertStatement(): RhovasAst.Statement.Assert {
        require(match("assert"))
        context.addLast(tokens[-1]!!.range)
        val condition = parseExpression()
        val message = if (match(":")) parseExpression() else null
        requireSemicolon { "An assert statement must be followed by a semicolon, as in `assert condition;`." }
        context.removeLast()
        return RhovasAst.Statement.Assert(condition, message)
    }

    private fun parseRequireStatement(): RhovasAst.Statement.Require {
        require(match("require"))
        context.addLast(tokens[-1]!!.range)
        val condition = parseExpression()
        val message = if (match(":")) parseExpression() else null
        requireSemicolon { "A require statement must be followed by a semicolon, as in `require condition;`." }
        context.removeLast()
        return RhovasAst.Statement.Require(condition, message)
    }

    private fun parseEnsureStatement(): RhovasAst.Statement.Ensure {
        require(match("ensure"))
        context.addLast(tokens[-1]!!.range)
        val condition = parseExpression()
        val message = if (match(":")) parseExpression() else null
        requireSemicolon { "An ensure statement must be followed by a semicolon, as in `ensure condition;`." }
        context.removeLast()
        return RhovasAst.Statement.Ensure(condition, message)
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
        //TODO: Use AST for first/last context
        var receiverContext = tokens[0]?.range
        var expression = parser()
        context.addLast(receiverContext!!)
        while (true) {
            context.addLast(receiverContext!!)
            receiverContext = tokens[0]?.range
            val operator = operators.sorted().lastOrNull { o ->
                match(*o.toCharArray().map { it.toString() }.toTypedArray())
            } ?: break
            context.addLast(receiverContext!!)
            val right = parser()
            expression = RhovasAst.Expression.Binary(operator, expression, right)
            context.removeLast()
            context.removeLast()
        }
        context.removeLast()
        context.removeLast()
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
        //TODO: Use AST for first/last context
        var receiverContext = tokens[0]?.range
        var expression = parsePrimaryExpression()
        context.addLast(receiverContext!!)
        while (true) {
            context.addLast(receiverContext!!)
            receiverContext = tokens[0]?.range
            expression = when {
                peek(".") || peek("?", ".") -> {
                    val nullable = match("?")
                    require(match("."))
                    val coalesce = match(".")
                    val pipeline = match("|")
                    parseAccessOrFunctionExpression(expression, nullable, coalesce, pipeline)
                }
                match("[") -> {
                    val arguments = mutableListOf<RhovasAst.Expression>()
                    while (!match("]")) {
                        arguments.add(parseExpression())
                        require(peek("]") || match(",")) { error(
                            "Expected closing bracket or comma.",
                            "An index expression must be followed by a closing bracket `]` or comma `,`, as in `expression[index]` or `expression[x, y, z]`.",
                        ) }
                    }
                    RhovasAst.Expression.Index(expression, arguments)
                }
                else -> break
            }
            context.removeLast()
        }
        context.removeLast()
        context.removeLast()
        return expression
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
                context.addLast(tokens[-1]!!.range)
                val elements = mutableListOf<RhovasAst.Expression>()
                while (!match("]")) {
                    elements.add(parseExpression())
                    context.addLast(tokens[-1]!!.range)
                    require(peek("]") || match(",")) { error(
                        "Expected closing bracket or comma.",
                        "A list literal element must be followed by a closing bracket `]` or comma `,`, as in `[element]` or `[x, y, z]`.",
                    ) }
                    context.removeLast()
                }
                context.removeLast()
                RhovasAst.Expression.Literal(elements)
            }
            match("{") -> {
                context.addLast(tokens[-1]!!.range)
                val properties = mutableMapOf<String, RhovasAst.Expression>()
                while (!match("}")) {
                    val key = parseIdentifier { "An object literal entry requires a key, as in `{key: value}` or `{x, y, z}`." }
                    context.addLast(tokens[-1]!!.range)
                    properties[key] = if (match(":")) parseExpression() else RhovasAst.Expression.Access(null, false, key)
                    context.addLast(tokens[-1]!!.range)
                    require(peek("}") || match(",")) { error(
                        "Expected closing parenthesis or comma.",
                        "An object literal entry must be followed by a closing parenthesis `}` or comma `,`, as in `{key: value}` or `{x, y, z}`.",
                    ) }
                    context.removeLast()
                    context.removeLast()
                }
                context.removeLast()
                RhovasAst.Expression.Literal(properties)
            }
            match("(") -> {
                val expression = parseExpression()
                require(match(")")) { error(
                    "Expected closing parenthesis.",
                    "A group expression must be followed by a closing parenthesis, as in `(expression)`.",
                ) }
                RhovasAst.Expression.Group(expression)
            }
            peek(RhovasTokenType.IDENTIFIER) -> parseAccessOrFunctionExpression(null, false, false, false)
            peek("#", RhovasTokenType.IDENTIFIER) -> parseMacroExpression()
            else -> throw error(
                "Expected expression.",
                "An expression must start with a literal (Null/Boolean/Integer/Decimal/String/Atom/List/Object), an opening parenthesis `(` for a group, an identifier for a variable/function, or a hashtag `#` followed by an identifier for a macro."
            )
        }
    }

    private fun parseAccessOrFunctionExpression(receiver: RhovasAst.Expression?, nullable: Boolean, coalesce: Boolean, pipeline: Boolean): RhovasAst.Expression {
        require(match(RhovasTokenType.IDENTIFIER))
        context.addLast(tokens[-1]!!.range)
        val name = StringBuilder(tokens[-1]!!.literal)
        //TODO: Proper qualified access
        if (pipeline) {
            while (match(".", RhovasTokenType.IDENTIFIER)) {
                name.append(".").append(tokens[-1]!!.literal)
            }
            require(peek("(")) { error(
                "Expected opening parenthesis.",
                "A pipelined function name must be followed by an opening parenthesis `(`, as in `receiver.|Component.function()`.",
            ) }
        }
        var arguments: MutableList<RhovasAst.Expression>? = null
        if (match("(")) {
            arguments = mutableListOf()
            while (!match(")")) {
                arguments.add(parseExpression())
                context.addLast(tokens[-1]!!.range)
                require(peek(")") || match(",")) { error(
                    "Expected closing parenthesis or comma.",
                    "An function argument must be followed by a closing parenthesis `}` or comma `,`, as in `function(argument)` or `function(x, y, z)`.",
                ) }
                context.removeLast()
            }
        }
        if (peek("|", RhovasTokenType.IDENTIFIER) || peek("{")) {
            arguments = arguments ?: mutableListOf()
            val parameters = mutableListOf<Pair<String, RhovasAst.Type?>>()
            if (match("|")) {
                context.addLast(tokens[-1]!!.range)
                while (!match("|")) {
                    val name = parseIdentifier { "A lambda parameter requires a name, as in `lambda |name| { ... }`." }
                    context.addLast(tokens[-1]!!.range)
                    val type = if (match(":")) parseType() else null
                    context.addLast(tokens[-1]!!.range)
                    parameters.add(Pair(name, type))
                    require(peek("|") || match(",")) { error(
                        "Expected closing pipe or comma.",
                        "A lambda parameter must be followed by a closing pipe `|` or comma `,`, as in `lambda |parameter| { ... }` or `lambda |x, y, z| { ... }`.",
                    ) }
                    context.removeLast()
                    context.removeLast()
                }
                context.removeLast()
            }
            require(peek("{")) { error(
                "Expected opening brace.",
                "A lambda's parameters must be followed by an opening brace `{`, as in `lambda |parameter| { ... }"
            ) }
            context.addLast(tokens[-1]!!.range)
            val body = parseStatement()
            arguments.add(RhovasAst.Expression.Lambda(parameters, body))
            context.removeLast()
        }
        return if (arguments == null) {
            require(!coalesce) { error(
                "Coalesce can only be used with a method.",
                "The coalesce operator returns the receiver of a method instead of the method's return value, and therefore can't be used with a field access since it cannot perform a side effect.",
            ) }
            context.removeLast()
            RhovasAst.Expression.Access(receiver, nullable, name.toString())
        } else {
            context.removeLast()
            RhovasAst.Expression.Function(receiver, nullable, coalesce, pipeline, name.toString(), arguments)
        }
    }

    private fun parseMacroExpression(): RhovasAst.Expression {
        require(match("#", RhovasTokenType.IDENTIFIER))
        context.addLast(tokens[-2]!!.range)
        val name = tokens[-1]!!.literal
        require(peek(listOf("(", "{"))) { error(
            "Expected opening parenthesis or brace.",
        "A macro expression must be followed by an opening parenthesis `(` or brace `{`, as in `#macro()` or `#dsl { ... }`.",
        ) }
        val arguments = mutableListOf<RhovasAst.Expression>()
        if (match("(")) {
            while (!match(")")) {
                context.addLast((tokens[0] ?: tokens[-1]!!).range)
                arguments.add(parseExpression())
                context.addLast(tokens[-1]!!.range)
                require(peek(")") || match(",")) { error(
                    "Expected closing parenthesis or comma.",
                    "An macro argument must be followed by a closing parenthesis `}` or comma `,`, as in `function(argument)` or `function(x, y, z)`.",
                ) }
                context.removeLast()
                context.removeLast()
            }
        }
        if (match("{")) {
            context.addLast(tokens[-1]!!.range)
            val parser = DslParser(lexer.input)
            parser.lexer.state = lexer.state.let {
                Pair(it.first.copy(index = it.first.index - 1, column = it.first.column - 1, length = 0), it.second)
            }
            val ast = parser.parse("source")
            lexer.state = parser.lexer.state.let {
                Pair(it.first.copy(index = it.first.index - 1, column = it.first.column - 1, length = 0), it.second)
            }
            require(match("}"))
            context.removeLast()
            arguments.add(RhovasAst.Expression.Dsl(name, ast))
        }
        return RhovasAst.Expression.Macro(name, arguments)
    }

    private fun parsePattern(): RhovasAst.Pattern {
        var pattern = when {
            peek(listOf("null", "true", "false", RhovasTokenType.INTEGER, RhovasTokenType.DECIMAL, RhovasTokenType.STRING))
                    || peek(":", RhovasTokenType.IDENTIFIER) -> RhovasAst.Pattern.Value(parsePrimaryExpression())
            peek(RhovasTokenType.IDENTIFIER) -> {
                if (tokens[0]!!.literal[0].isUpperCase()) {
                    context.addLast(tokens[-1]!!.range)
                    val type = parseType()
                    val pattern = if (peek(listOf(RhovasTokenType.IDENTIFIER, "[", "{", "$"))) parsePattern() else null
                    context.removeLast()
                    RhovasAst.Pattern.TypedDestructure(type, pattern)
                } else {
                    require(match(RhovasTokenType.IDENTIFIER))
                    val name = tokens[-1]!!.literal
                    RhovasAst.Pattern.Variable(name)
                }
            }
            match("[") -> {
                context.addLast(tokens[-1]!!.range)
                val patterns = mutableListOf<RhovasAst.Pattern>()
                while (!match("]")) {
                    patterns.add(parsePattern())
                    require(peek("]") || match(",")) { error(
                        "Expected closing bracket or comma.",
                        "An ordered destructuring element must be followed by a closing bracket `]` or comma `,`, as in `[argument]` or `[x, y, z]`.",
                    ) }
                }
                context.removeLast()
                RhovasAst.Pattern.OrderedDestructure(patterns)
            }
            match("{") -> {
                context.addLast(tokens[-1]!!.range)
                val patterns = mutableListOf<Pair<String, RhovasAst.Pattern?>>()
                while (!match("}")) {
                    context.addLast((tokens[0] ?: tokens[-1]!!).range)
                    if (peek(RhovasTokenType.IDENTIFIER, listOf("*", "+"))) {
                        val pattern = parsePattern()
                        patterns.add(Pair("", pattern))
                    } else {
                        val name = parseIdentifier { "A named destructuring entry requires a key, as in `{key: value}` or `{x, y, z}`." }
                        context.addLast(tokens[-1]!!.range)
                        val pattern = if (match(":")) parsePattern() else null
                        context.removeLast()
                        patterns.add(Pair(name, pattern))
                    }
                    require(peek("}") || match(",")) { error(
                        "Expected closing brace or comma.",
                        "A named destructuring entry must be followed by a closing brace `}` or comma `,`, as in `{key: value}` or `{x, y, z}`.",
                    ) }
                    context.removeLast()
                }
                context.removeLast()
                RhovasAst.Pattern.NamedDestructure(patterns)
            }
            peek(listOf("*", "+")) -> null
            match("$") -> {
                require(match("{")) { error(
                    "Expected opening brace.",
                    "A value pattern uses interpolation which requires braces around the expression, as in `\${value}`.",
                ) }
                val value = parseExpression()
                require(match("}")) { error(
                    "Expected opening brace.",
                    "A value pattern uses interpolation which requires braces around the expression, as in `\${value}`.",
                ) }
                RhovasAst.Pattern.Value(value)
            }
            else -> throw error(
                "Expected pattern.",
                "A pattern must start with a literal (Null/Boolean/Integer/Decimal/String/Atom/List/Object), a star `*` or plus `+` for varargs, or a dollar sign `$` for interpolation.",
            )
        }
        if (match(listOf("*", "+"))) {
            val operator = tokens[-1]!!.literal
            pattern = RhovasAst.Pattern.VarargDestructure(pattern, operator)
        }
        if (match("$")) {
            context.addLast(tokens[-1]!!.range)
            require(match("{")) { error(
                "Expected opening brace.",
                "A predicate pattern uses interpolation which requires braces around the expression, as in `\${predicate}`.",
            ) }
            val predicate = parseExpression()
            require(match("}")) { error(
                "Expected closing brace.",
                "A predicate pattern uses interpolation which requires braces around the expression, as in `\${predicate}`.",
            ) }
            context.removeLast()
            pattern = RhovasAst.Pattern.Predicate(pattern!!, predicate)
        }
        return pattern!!
    }

    private fun parseType(): RhovasAst.Type {
        val name = parseIdentifier { "A type requires a name, as in `Type`." }
        return RhovasAst.Type(name)
    }

    private fun parseInterpolation(): RhovasAst.Expression {
        require(match("$", "{"))
        val expression = parseExpression()
        require(match("}")) { error(
            "Expected closing brace.",
            "An interpolated value requires braces around the expression, as in `\${value}`.",
        ) }
        return expression
    }

    private fun parseIdentifier(details: () -> String): String {
        require(match(RhovasTokenType.IDENTIFIER)) { error("Expected identifier.", details()) }
        return tokens[-1]!!.literal
    }

    private fun requireSemicolon(details: () -> String) {
        context.addLast(tokens[-1]!!.range)
        require(match(";")) { error("Expected semicolon.", details()) }
        context.removeLast()
    }

}
