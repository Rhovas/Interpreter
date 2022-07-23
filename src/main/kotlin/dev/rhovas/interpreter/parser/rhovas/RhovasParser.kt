package dev.rhovas.interpreter.parser.rhovas

import dev.rhovas.interpreter.parser.Input
import dev.rhovas.interpreter.parser.Parser
import dev.rhovas.interpreter.parser.dsl.DslParser

class RhovasParser(input: Input) : Parser<RhovasTokenType>(RhovasLexer(input)) {

    override fun parse(rule: String): RhovasAst {
        return when (rule) {
            "source" -> parseSource()
            "component" -> parseComponent()
            "statement" -> parseStatement()
            "expression" -> parseExpression()
            "pattern" -> parsePattern()
            "type" -> parseType()
            "interpolation" -> parseInterpolation()
            else -> throw AssertionError()
        }.also {
            require(rule == "interpolation" || tokens[0] == null) { error(
                "Expected end of input.",
                "Parsing for the `${rule}` rule completed without consuming all input. This is normally an implementation problem.",
            ) }
        }
    }

    private fun parseSource(): RhovasAst.Source {
        val statements = mutableListOf<RhovasAst.Statement>()
        while (tokens[0] != null) {
            statements.add(parseStatement())
        }
        return RhovasAst.Source(statements).also {
            it.context = if (statements.isNotEmpty()) listOf(statements[0].context.first(), tokens[-1]!!.range) else listOf()
        }
    }

    private fun parseComponent(): RhovasAst.Component {
        return when {
            peek("struct") -> parseStruct()
            else -> throw AssertionError()
        }
    }

    private fun parseStruct(): RhovasAst.Component.Struct {
        require(match("struct"))
        context.addLast(tokens[-1]!!.range)
        val name = parseIdentifier { "A struct requires a name after `struct`, as in `struct Name { ... }`." }
        require(match("{")) { error(
            "Expected opening brace.",
            "A struct requires braces for defining members, as in `struct Name { ... }`.",
        ) }
        val fields = mutableListOf<RhovasAst.Statement.Declaration>()
        while (!match("}")) {
            require(peek(listOf("val", "var"))) { error(
                "Expected variable declaration.",
                "A struct requires variable declarations, as in `struct Name { val field: Type; }`.",
            ) }
            fields.add(parseDeclarationStatement())
        }
        return RhovasAst.Component.Struct(name, fields).also {
            it.context = listOf(context.removeLast(), tokens[-1]!!.range)
        }
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
            peek(listOf("struct")) -> {
                RhovasAst.Statement.Component(parseComponent()).also {
                    it.context = it.component.context
                }
            }
            else -> {
                val expression = parseExpression()
                if (match("=")) {
                    val value = parseExpression()
                    requireSemicolon { "An assignment statement must be followed by a semicolon, as in `name = value;`." }
                    RhovasAst.Statement.Assignment(expression, value).also {
                        it.context = listOf(expression.context.first(), tokens[-1]!!.range)
                    }
                } else {
                    requireSemicolon { "An expression statement must be followed by a semicolon, as in `expression;`" }
                    RhovasAst.Statement.Expression(expression).also {
                        it.context = listOf(expression.context.first(), tokens[-1]!!.range)
                    }
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
        return RhovasAst.Statement.Block(statements).also {
            it.context = listOf(context.removeLast(), tokens[-1]!!.range)
        }
    }

    private fun parseFunctionStatement(): RhovasAst.Statement.Function {
        require(match("func"))
        context.addLast(tokens[-1]!!.range)
        val name = parseIdentifier { "A function declaration requires a name after `func`, as in `func name() { ... }`." }
        val generics = mutableListOf<Pair<String, RhovasAst.Type?>>()
        if (match("<")) {
            do {
                val name = parseIdentifier { "A function generic type declaration requires a name, as in `func name<T>() { ... }` or `func name<X, Y, Z>() { ... }`." }
                context.addLast(tokens[-1]!!.range)
                val type = if (match(":")) parseType() else null
                generics.add(Pair(name, type))
                require(peek(">") || match(",")) { error(
                    "Expected closing angle bracket or comma.",
                    "A function generic type declaration must be followed by a closing angle bracket `>` or comma `,`, as in `func name<T>() { ... }` or `func name<X, Y, Z>() { ... }`.",
                ) }
                context.removeLast()
            } while (!match(">"))
        }
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
        val throws = mutableListOf<RhovasAst.Type>()
        if (match("throws")) {
            do {
                throws.add(parseType())
            } while (match(","))
        }
        val body = parseStatement()
        return RhovasAst.Statement.Function(name, generics, parameters, returns, throws, body).also {
            it.context = listOf(context.removeLast(), tokens[-1]!!.range)
        }
    }

    private fun parseDeclarationStatement(): RhovasAst.Statement.Declaration {
        require(match(listOf("val", "var")))
        context.addLast(tokens[-1]!!.range)
        val mutable = tokens[-1]!!.literal == "var"
        val name = parseIdentifier { "A variable declaration requires a name after `val`/`var`, as in `val name = value;` or `var name: Type;`." }
        val type = if (match(":")) parseType() else null
        val value = if (match("=")) parseExpression() else null
        requireSemicolon { "A variable declaration must be followed by a semicolon, as in `val name = value;` or `var name: Type;`." }
        return RhovasAst.Statement.Declaration(mutable, name, type, value).also {
            it.context = listOf(context.removeLast(), tokens[-1]!!.range)
        }
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
        return RhovasAst.Statement.If(condition, thenStatement, elseStatement).also {
            it.context = listOf(context.removeLast(), tokens[-1]!!.range)
        }
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
        return RhovasAst.Statement.Match.Conditional(cases, elseCase).also {
            it.context = listOf(context.removeLast(), tokens[-1]!!.range)
        }
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
        return RhovasAst.Statement.Match.Structural(argument, cases, elseCase).also {
            it.context = listOf(context.removeLast(), tokens[-1]!!.range)
        }
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
        return RhovasAst.Statement.For(name, iterable, body).also {
            it.context = listOf(context.removeLast(), tokens[-1]!!.range)
        }
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
        return RhovasAst.Statement.While(condition, body).also {
            it.context = listOf(context.removeLast(), tokens[-1]!!.range)
        }
    }

    private fun parseTryStatement(): RhovasAst.Statement.Try {
        require(match("try"))
        context.addLast(tokens[-1]!!.range)
        val body = parseStatement()
        val catches = mutableListOf<RhovasAst.Statement.Try.Catch>()
        while (peek("catch")) {
            catches.add(parseTryStatementCatch())
        }
        val finallyStatement = if (match("finally")) {
            context.addLast(tokens[-1]!!.range)
            val finallyStatement = parseStatement()
            context.removeLast()
            finallyStatement
        } else null
        return RhovasAst.Statement.Try(body, catches, finallyStatement).also {
            it.context = listOf(context.removeLast(), tokens[-1]!!.range)
        }
    }

    private fun parseTryStatementCatch(): RhovasAst.Statement.Try.Catch {
        require(match("catch"))
        context.addLast(tokens[-1]!!.range)
        require(match("(")) { error(
            "Expected opening parenthesis.",
            "A catch block requires parenthesis around the argument, as in `try { ... } catch (val name: Type) { ... }`.",
        ) }
        require(match("val")) { error(
            "Expected `val`.",
            "A catch block variable requires `val`, as in `try { ... } catch (val name: Type) { ... }`.",
        ) }
        val name = parseIdentifier { "A catch block variable requires a name, as in `try { ... } catch (val name: Type) { ... }`." }
        require(match(":")) { error(
            "Expected colon.",
            "A catch block variable a colon before the type, as in `try { ... } catch (val name: Type) { ... }`.",
        ) }
        val type = parseType()
        require(match(")")) { error(
            "Expected closing parenthesis.",
            "A catch block requires parenthesis around the argument, as in `try { ... } catch (val name: Type) { ... }`.",
        ) }
        val body = parseStatement()
        return RhovasAst.Statement.Try.Catch(name, type, body).also {
            it.context = listOf(context.removeLast(), tokens[-1]!!.range)
        }
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
        return RhovasAst.Statement.With(name, argument, body).also {
            it.context = listOf(context.removeLast(), tokens[-1]!!.range)
        }
    }

    private fun parseLabelStatement(): RhovasAst.Statement.Label {
        require(match(RhovasTokenType.IDENTIFIER))
        context.addLast(tokens[-1]!!.range)
        val label = tokens[-1]!!.literal
        require(match(":"))
        val statement = parseStatement()
        return RhovasAst.Statement.Label(label, statement).also {
            it.context = listOf(context.removeLast(), tokens[-1]!!.range)
        }
    }

    private fun parseBreakStatement(): RhovasAst.Statement.Break {
        require(match("break"))
        context.addLast(tokens[-1]!!.range)
        val label = if (match(RhovasTokenType.IDENTIFIER)) tokens[-1]!!.literal else null
        requireSemicolon { "A break statement must be followed by a semicolon, as in `break;` or `break label;`." }
        return RhovasAst.Statement.Break(label).also {
            it.context = listOf(context.removeLast(), tokens[-1]!!.range)
        }
    }

    private fun parseContinueStatement(): RhovasAst.Statement.Continue {
        require(match("continue"))
        context.addLast(tokens[-1]!!.range)
        val label = if (match(RhovasTokenType.IDENTIFIER)) tokens[-1]!!.literal else null
        requireSemicolon { "A continue statement must be followed by a semicolon, as in `continue;` or `continue label;`." }
        return RhovasAst.Statement.Continue(label).also {
            it.context = listOf(context.removeLast(), tokens[-1]!!.range)
        }
    }

    private fun parseReturnStatement(): RhovasAst.Statement.Return {
        require(match("return"))
        context.addLast(tokens[-1]!!.range)
        val value = if (!peek(";")) parseExpression() else null
        requireSemicolon { "A return statement must be followed by a semicolon, as in `return;` or `return value;`." }
        return RhovasAst.Statement.Return(value).also {
            it.context = listOf(context.removeLast(), tokens[-1]!!.range)
        }
    }

    private fun parseThrowStatement(): RhovasAst.Statement.Throw {
        require(match("throw"))
        context.addLast(tokens[-1]!!.range)
        val exception = parseExpression()
        requireSemicolon { "A throw statement must be followed by a semicolon, as in `throw exception;`." }
        return RhovasAst.Statement.Throw(exception).also {
            it.context = listOf(context.removeLast(), tokens[-1]!!.range)
        }
    }

    private fun parseAssertStatement(): RhovasAst.Statement.Assert {
        require(match("assert"))
        context.addLast(tokens[-1]!!.range)
        val condition = parseExpression()
        val message = if (match(":")) parseExpression() else null
        requireSemicolon { "An assert statement must be followed by a semicolon, as in `assert condition;`." }
        return RhovasAst.Statement.Assert(condition, message).also {
            it.context = listOf(context.removeLast(), tokens[-1]!!.range)
        }
    }

    private fun parseRequireStatement(): RhovasAst.Statement.Require {
        require(match("require"))
        context.addLast(tokens[-1]!!.range)
        val condition = parseExpression()
        val message = if (match(":")) parseExpression() else null
        requireSemicolon { "A require statement must be followed by a semicolon, as in `require condition;`." }
        return RhovasAst.Statement.Require(condition, message).also {
            it.context = listOf(context.removeLast(), tokens[-1]!!.range)
        }
    }

    private fun parseEnsureStatement(): RhovasAst.Statement.Ensure {
        require(match("ensure"))
        context.addLast(tokens[-1]!!.range)
        val condition = parseExpression()
        val message = if (match(":")) parseExpression() else null
        requireSemicolon { "An ensure statement must be followed by a semicolon, as in `ensure condition;`." }
        return RhovasAst.Statement.Ensure(condition, message).also {
            it.context = listOf(context.removeLast(), tokens[-1]!!.range)
        }
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
        context.addLast(expression.context.first())
        while (true) {
            context.addLast(expression.context.first())
            val operator = operators.sorted().lastOrNull { o ->
                match(*o.toCharArray().map { it.toString() }.toTypedArray())
            } ?: break
            context.addLast(tokens[-1]!!.range)
            val right = parser()
            expression = RhovasAst.Expression.Binary(operator, expression, right).also {
                it.context = listOf(context[context.size - 3], right.context.last())
            }
            context.removeLast()
            context.removeLast()
        }
        context.removeLast()
        context.removeLast()
        return expression
    }

    private fun parseUnaryExpression(): RhovasAst.Expression {
        return if (match(listOf("-", "!"))) {
            context.addLast(tokens[-1]!!.range)
            val operator = tokens[-1]!!.literal
            val expression = parseUnaryExpression()
            RhovasAst.Expression.Unary(operator, expression).also {
                it.context = listOf(context.removeLast(), tokens[-1]!!.range)
            }
        } else {
            parseSecondaryExpression()
        }
    }

    private fun parseSecondaryExpression(): RhovasAst.Expression {
        var expression = parsePrimaryExpression()
        context.addLast(expression.context.first())
        while (true) {
            context.addLast(expression.context.first())
            expression = when {
                peek(".") || peek("?", ".") -> {
                    val coalesce = match("?")
                    require(match("."))
                    val cascade = match(".")
                    if (match("|")) {
                        var name = parseIdentifier { "A pipeline expression requires a name, as in `receiver.property` or `receiver.method()`." }
                        context.addLast(tokens[-1]!!.range)
                        var qualifier: RhovasAst.Expression.Access? = null
                        while (match(".", RhovasTokenType.IDENTIFIER)) {
                            qualifier = if (qualifier == null) {
                                RhovasAst.Expression.Access.Variable(name).also {
                                    it.context = listOf(tokens[-3]!!.range)
                                }
                            } else {
                                RhovasAst.Expression.Access.Property(qualifier, false, name).also {
                                    it.context = listOf(qualifier!!.context.first(), tokens[-3]!!.range)
                                }
                            }
                            name = tokens[-1]!!.literal
                        }
                        require(peek(listOf("(", "|", "{"))) { error(
                            "Expected opening parenthesis, pipe, or brace.",
                            "A pipeline expression requires an invocation, as in `receiver.|function()`, `receiver.|function |name| { ... }`, or `receiver.|function { ... }`.",
                        ) }
                        val arguments = parseInvokeExpressionArguments()
                        RhovasAst.Expression.Invoke.Pipeline(expression, coalesce, cascade, qualifier, name, arguments).also {
                            it.context = listOf(context.removeLast(), tokens[-1]!!.range)
                        }
                    } else {
                        val name = parseIdentifier { "A property or method expression requires a name, as in `receiver.property` or `receiver.method()`." }
                        context.addLast(tokens[-1]!!.range)
                        if (peek(listOf("(", "|", "{"))) {
                            val arguments = parseInvokeExpressionArguments()
                            RhovasAst.Expression.Invoke.Method(expression, coalesce, cascade, name, arguments).also {
                                it.context = listOf(context.removeLast(), tokens[-1]!!.range)
                            }
                        } else {
                            require(!cascade) { error(
                                "Expected opening parenthesis, pipe, or brace.",
                                "A pipeline expression requires an invocation, as in `receiver.|function()`, `receiver.|function |name| { ... }`, or `receiver.|function { ... }`.",
                            ) }
                            RhovasAst.Expression.Access.Property(expression, coalesce, name).also {
                                it.context = listOf(context.removeLast(), tokens[-1]!!.range)
                            }
                        }
                    }
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
                    RhovasAst.Expression.Access.Index(expression, arguments).also {
                        it.context = listOf(context[context.size - 2], tokens[-1]!!.range)
                    }
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
            match("null") -> {
                RhovasAst.Expression.Literal.Scalar(null).also {
                    it.context = listOf(tokens[-1]!!.range)
                }
            }
            match(listOf("true", "false")) -> {
                RhovasAst.Expression.Literal.Scalar(tokens[-1]!!.literal.toBooleanStrict()).also {
                    it.context = listOf(tokens[-1]!!.range)
                }
            }
            match(listOf(RhovasTokenType.INTEGER, RhovasTokenType.DECIMAL)) -> {
                RhovasAst.Expression.Literal.Scalar(tokens[-1]!!.value).also {
                    it.context = listOf(tokens[-1]!!.range)
                }
            }
            match("\"") -> {
                context.addLast(tokens[-1]!!.range)
                lexer.mode = "string"
                val literals = mutableListOf<String>()
                val arguments = mutableListOf<RhovasAst.Expression>()
                if (!peek(RhovasTokenType.STRING)) {
                    literals.add("")
                }
                while (!match("\"")) {
                    if (match("\${")) {
                        context.addLast(tokens[-1]!!.range)
                        lexer.mode = ""
                        arguments.add(parseExpression())
                        require(match("}")) { error(
                            "Expected closing brace.",
                            "An interpolated argument must be followed by a closing brace, as in `\"variable = \${variable}\"`.",
                        ) }
                        lexer.mode = "string"
                        if (!peek(RhovasTokenType.STRING)) {
                            literals.add("")
                        }
                        context.removeLast()
                    } else {
                        require(match(RhovasTokenType.STRING)) { error(
                            "Unterminated string literal.",
                            "A string literal must end with a double quote (\") and cannot span multiple lines.",
                        ) }
                        literals.add(tokens[-1]!!.value as String)
                    }
                }
                lexer.mode = ""
                RhovasAst.Expression.Literal.String(literals, arguments).also {
                    it.context = listOf(context.removeLast(), tokens[-1]!!.range)
                }
            }
            match(":", RhovasTokenType.IDENTIFIER) -> {
                RhovasAst.Expression.Literal.Scalar(RhovasAst.Atom(tokens[-1]!!.literal)).also {
                    it.context = listOf(tokens[-2]!!.range, tokens[-1]!!.range)
                }
            }
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
                RhovasAst.Expression.Literal.List(elements).also {
                    it.context = listOf(context.removeLast(), tokens[-1]!!.range)
                }
            }
            match("{") -> {
                context.addLast(tokens[-1]!!.range)
                val properties = mutableMapOf<String, RhovasAst.Expression>()
                while (!match("}")) {
                    val key = parseIdentifier { "An object literal entry requires a key, as in `{key: value}` or `{x, y, z}`." }
                    context.addLast(tokens[-1]!!.range)
                    properties[key] = if (match(":")) parseExpression() else RhovasAst.Expression.Access.Variable(key).also {
                        it.context = listOf(tokens[-1]!!.range)
                    }
                    context.addLast(tokens[-1]!!.range)
                    require(peek("}") || match(",")) { error(
                        "Expected closing parenthesis or comma.",
                        "An object literal entry must be followed by a closing parenthesis `}` or comma `,`, as in `{key: value}` or `{x, y, z}`.",
                    ) }
                    context.removeLast()
                    context.removeLast()
                }
                RhovasAst.Expression.Literal.Object(properties).also {
                    it.context = listOf(context.removeLast(), tokens[-1]!!.range)
                }
            }
            match("(") -> {
                context.addLast(tokens[-1]!!.range)
                val expression = parseExpression()
                require(match(")")) { error(
                    "Expected closing parenthesis.",
                    "A group expression must be followed by a closing parenthesis, as in `(expression)`.",
                ) }
                RhovasAst.Expression.Group(expression).also {
                    it.context = listOf(context.removeLast(), tokens[-1]!!.range)
                }
            }
            match(RhovasTokenType.IDENTIFIER) -> {
                context.addLast(tokens[-1]!!.range)
                val name = tokens[-1]!!.literal
                if (peek(listOf("(", "{")) || peek("|", RhovasTokenType.IDENTIFIER)) {
                    val arguments = parseInvokeExpressionArguments()
                    RhovasAst.Expression.Invoke.Function(name, arguments).also {
                        it.context = listOf(context.removeLast(), tokens[-1]!!.range)
                    }
                } else {
                    RhovasAst.Expression.Access.Variable(name).also {
                        it.context = listOf(context.removeLast(), tokens[-1]!!.range)
                    }
                }
            }
            peek("#", RhovasTokenType.IDENTIFIER) -> parseMacroExpression()
            else -> throw error(
                "Expected expression.",
                "An expression must start with a literal (Null/Boolean/Integer/Decimal/String/Atom/List/Object), an opening parenthesis `(` for a group, an identifier for a variable/function, or a hashtag `#` followed by an identifier for a macro."
            )
        }
    }

    private fun parseInvokeExpressionArguments(): List<RhovasAst.Expression> {
        require(peek(listOf("(", "|", "{")))
        val arguments = mutableListOf<RhovasAst.Expression>()
        if (match("(")) {
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
            context.addLast(tokens[0]!!.range)
            val parameters = mutableListOf<Pair<String, RhovasAst.Type?>>()
            if (match("|")) {
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
            }
            require(peek("{")) { error(
                "Expected opening brace.",
                "A lambda's parameters must be followed by an opening brace `{`, as in `lambda |parameter| { ... }"
            ) }
            context.addLast(tokens[-1]!!.range)
            val body = parseStatement()
            context.removeLast()
            arguments.add(RhovasAst.Expression.Lambda(parameters, body).also {
                it.context = listOf(context.removeLast(), tokens[-1]!!.range)
            })
        }
        return arguments
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
            arguments.add(RhovasAst.Expression.Dsl(name, ast).also {
                it.context = listOf(context.removeLast(), tokens[-1]!!.range)
            })
        }
        return RhovasAst.Expression.Macro(name, arguments).also {
            it.context = listOf(context.removeLast(), tokens[-1]!!.range)
        }
    }

    private fun parsePattern(): RhovasAst.Pattern {
        var pattern = when {
            peek(listOf("null", "true", "false", RhovasTokenType.INTEGER, RhovasTokenType.DECIMAL)) ||
            peek("\"") ||
            peek(":", RhovasTokenType.IDENTIFIER) -> {
                RhovasAst.Pattern.Value(parsePrimaryExpression()).also {
                    it.context = it.value.context.toMutableList()
                }
            }
            peek(RhovasTokenType.IDENTIFIER) -> {
                if (tokens[0]!!.literal[0].isUpperCase()) {
                    context.addLast(tokens[0]!!.range)
                    val type = parseType()
                    val pattern = if (peek(listOf(RhovasTokenType.IDENTIFIER, "[", "{", "$"))) parsePattern() else null
                    RhovasAst.Pattern.TypedDestructure(type, pattern).also {
                        it.context = listOf(context.removeLast(), tokens[-1]!!.range)
                    }
                } else {
                    require(match(RhovasTokenType.IDENTIFIER))
                    val name = tokens[-1]!!.literal
                    RhovasAst.Pattern.Variable(name).also {
                        it.context = listOf(tokens[-1]!!.range)
                    }
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
                RhovasAst.Pattern.OrderedDestructure(patterns).also {
                    it.context = listOf(context.removeLast(), tokens[-1]!!.range)
                }
            }
            match("{") -> {
                context.addLast(tokens[-1]!!.range)
                val patterns = mutableListOf<Pair<String, RhovasAst.Pattern?>>()
                while (!match("}")) {
                    context.addLast((tokens[0] ?: tokens[-1]!!).range)
                    //TODO: Consider allowing other patterns for unnamed varargs (e.g. {[x, y, z]*})
                    if (peek(listOf("*", "+")) || peek(RhovasTokenType.IDENTIFIER, listOf("*", "+"))) {
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
                RhovasAst.Pattern.NamedDestructure(patterns).also {
                    it.context = listOf(context.removeLast(), tokens[-1]!!.range)
                }
            }
            peek(listOf("*", "+")) -> null
            match("$") -> {
                context.addLast(tokens[-1]!!.range)
                require(match("{")) { error(
                    "Expected opening brace.",
                    "A value pattern uses interpolation which requires braces around the expression, as in `\${value}`.",
                ) }
                val value = parseExpression()
                require(match("}")) { error(
                    "Expected opening brace.",
                    "A value pattern uses interpolation which requires braces around the expression, as in `\${value}`.",
                ) }
                RhovasAst.Pattern.Value(value).also {
                    it.context = listOf(context.removeLast(), tokens[-1]!!.range)
                }
            }
            else -> throw error(
                "Expected pattern.",
                "A pattern must start with a literal (Null/Boolean/Integer/Decimal/String/Atom/List/Object), a star `*` or plus `+` for varargs, or a dollar sign `$` for interpolation.",
            )
        }
        if (match(listOf("*", "+"))) {
            val operator = tokens[-1]!!.literal
            pattern = RhovasAst.Pattern.VarargDestructure(pattern, operator).also {
                it.context = if (pattern != null) listOf(pattern!!.context.first(), tokens[-1]!!.range) else listOf(tokens[-1]!!.range)
            }
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
            pattern = RhovasAst.Pattern.Predicate(pattern!!, predicate).also {
                it.context = listOf(pattern!!.context.first(), tokens[-1]!!.range)
            }
        }
        return pattern!!
    }

    private fun parseType(): RhovasAst.Type {
        val name = parseIdentifier { "A type requires a name, as in `Type`." }
        var generics: MutableList<RhovasAst.Type>? = null
        if (match("<")) {
            generics = mutableListOf()
            while (!match(">")) {
                generics.add(parseType())
                require(match(",") || peek(">")) { error(
                    "Expected closing bracket or comma.",
                    "A generic parameter must be followed by a closing bracket `>` or comma `,`, as in `Type<T>` or `Type<X, Y, Z>`",
                ) }
            }
        }
        return RhovasAst.Type(name, generics).also {
            it.context = listOf(tokens[-1]!!.range)
        }
    }

    private fun parseInterpolation(): RhovasAst.Expression.Interpolation {
        require(match("$", "{"))
        context.addLast(tokens[-2]!!.range)
        val expression = parseExpression()
        require(match("}")) { error(
            "Expected closing brace.",
            "An interpolated value requires braces around the expression, as in `\${value}`.",
        ) }
        return RhovasAst.Expression.Interpolation(expression).also {
            it.context = listOf(context.removeLast(), tokens[-1]!!.range)
        }
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
