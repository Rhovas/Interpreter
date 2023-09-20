package dev.rhovas.interpreter.parser.rhovas

import dev.rhovas.interpreter.environment.Modifiers
import dev.rhovas.interpreter.parser.Input
import dev.rhovas.interpreter.parser.ParseException
import dev.rhovas.interpreter.parser.Parser
import dev.rhovas.interpreter.parser.dsl.DslAst
import dev.rhovas.interpreter.parser.dsl.DslParser

class RhovasParser(input: Input) : Parser<RhovasTokenType>(RhovasLexer(input)) {

    /**
     * Dispatches to the appropriate `parse` method. All rules except
     * `"interpolation"` are expected to consume the entire input.
     */
    override fun parse(rule: String): RhovasAst {
        return when (rule) {
            "source" -> parseSource()
            "component" -> parseComponent()
            "member" -> parseMember()
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

    /**
     *  - `source = import* statement*`
     */
    private fun parseSource(): RhovasAst.Source {
        val start = tokens[0]?.range
        val imports = generateSequence {
            if (peek("import")) parseImport() else null
        }.toList()
        val statements = generateSequence {
            tokens[0]?.let { parseStatement() }
        }.toList()
        return RhovasAst.Source(imports, statements).also {
            it.context = listOfNotNull(start, tokens[-1]?.range)
        }
    }

    /**
     *  - `import = identifier ("." identifier)* ("as" identifier)? ";"`
     */
    private fun parseImport(): RhovasAst.Import = parseAst {
        require(match("import"))
        val path = parseSequence(".") { parseIdentifier { "An import requires a name, as in `import Module.Type;`." } }
        val alias = if (match("as")) parseIdentifier { "An import alias requires a name, as in `import Module.Type as Alias;`." } else null
        requireSemicolon { "An import must be followed by a semicolon, as in `import Module.Type;`." }
        RhovasAst.Import(path, alias)
    }

    /**
     *  - `modifiers = inheritance? override?`
     *     - `inheritance = 'virtual' | 'abstract'`
     *     - `override = 'override'`
     */
    private fun parseModifiers(): Modifiers {
        val inheritance = when {
            match("virtual") -> Modifiers.Inheritance.VIRTUAL
            match("abstract") -> Modifiers.Inheritance.ABSTRACT
            else -> Modifiers.Inheritance.FINAL
        }
        val override = match("override")
        return Modifiers(inheritance, override)
    }

    /**
     *  - `component = modifiers term inherits? "{" member* "}"`
     *     - term = "struct" | "class" | "interface"
     *     - inherits = ":" type ("," type)*
     */
    private fun parseComponent(): RhovasAst.Component = parseAst {
        val modifiers = parseModifiers()
        require(match(listOf("struct", "class", "interface")))
        val term = tokens[-1]!!.literal
        val name = parseIdentifier { "A component requires a name, as in `struct Name { ... }`." }
        val generics = parseSequence("<", ",", ">") {
            val name = parseIdentifier { "A component generic type declaration requires a name, as in `struct Name<T>() { ... }` or `struct Name<T: Bound> { ... }`." }
            val type = if (match(":")) parseType() else null
            require(peek(listOf(",", ">"))) { error(
                "Expected closing angle bracket or comma.",
                "A component generic type declaration must be followed by a closing angle bracket `>` or comma `,`, as in `struct Name<T>() { ... }` or `class Name<T: Bound> { ... }`.",
            ) }
            Pair(name, type)
        } ?: listOf()
        val inherits = if (match(":")) {
            parseSequence(",") { parseType() }
        } else listOf()
        require(peek("{")) { error(
            "Expected opening brace.",
            "A component requires braces for defining members, as in `struct Name { ... }`.",
        ) }
        val members = parseSequence("{", null, "}") { parseMember() }!!
        when (term) {
            "struct" -> RhovasAst.Component.Struct(modifiers, name, generics, inherits, members)
            "class" -> RhovasAst.Component.Class(modifiers, name, generics, inherits, members)
            "interface" -> RhovasAst.Component.Interface(modifiers, name, generics, inherits, members)
            else -> throw AssertionError()
        }
    }

    /**
     *  - `member = property | initializer | method`
     */
    private fun parseMember(): RhovasAst.Member {
        val modifiers = parseModifiers()
        return when {
            peek(listOf("val", "var")) -> parsePropertyMember(modifiers)
            peek("init") -> parseInitializerMember(modifiers)
            peek("func") -> parseMethodMember(modifiers)
            else -> throw error(
                "Expected member.",
                "A member is either a property (`val`/`var`), initializer (`init`), or method (`func`).",
            )
        }
    }

    /**
     *  - `property = ("val" | "var") identifier ":" type ("=" expression)? ";"`
     */
    private fun parsePropertyMember(modifiers: Modifiers): RhovasAst.Member.Property = parseAst {
        require(match(listOf("val", "var")))
        val mutable = tokens[-1]!!.literal == "var"
        val name = parseIdentifier { "A property member requires a name after `val`/`var`, as in `val name: Type = value;` or `var name: Type;`." }
        require(match(":")) { error(
            "Expected colon",
            "A property member requires a type, as in `val name: Type = value;` or `var name: Type`.",
        ) }
        val type = parseType()
        val value = if (match("=")) parseExpression() else null
        requireSemicolon { "A property member must be followed by a semicolon, as in `val name: Type = value;` or `var name: Type;`." }
        RhovasAst.Member.Property(modifiers, mutable, name, type, value)
    }

    /**
     *  - `initializer = "init" parameters returns? throws? statement`
     *     - `parameters = "(" (parameter ("," parameter)* ","?) ")"`
     *        - `parameter = identifier (":" type)?`
     *     - `returns = ":" type`
     *     - `throws = "throws" type ("," type)*`
     */
    private fun parseInitializerMember(modifiers: Modifiers): RhovasAst.Member.Initializer = parseAst {
        require(match("init"))
        require(peek("(")) { error(
            "Expected opening parenthesis.",
            "An initializer requires parenthesis after the name, as in `init() { ... }` or `init(x, y, z) { ... }`.",
        ) }
        val parameters = parseSequence("(", ",", ")") {
            val name = parseIdentifier { "An initializer parameter requires a name, as in `init(name: Type) { ... }`." }
            val type = if (match(":")) parseType() else null
            require(peek(listOf(",", ")"))) { error(
                "Expected closing parenthesis or comma.",
                "An initializer parameter must be followed by a closing parenthesis `)` or comma `,`, as in `init() { ... }` or `init(x, y, z) { ... }`.",
            ) }
            Pair(name, type)
        }!!
        val returns = if (match(":")) parseType() else null
        val throws = if (match("throws")) parseSequence(",") { parseType() } else listOf()
        val block = parseBlockStatement()
        RhovasAst.Member.Initializer(modifiers, parameters, returns, throws, block)
    }

    /**
     * - `method = function`
     */
    private fun parseMethodMember(modifiers: Modifiers): RhovasAst.Member.Method = parseAst {
        RhovasAst.Member.Method(modifiers, parseFunctionDeclarationStatement())
    }

    /**
     * Dispatches to the appropriate `parse` method, falling back to an
     * expression / assignment statement. Semicolons following expression
     * statements are optional if at the end of a block.
     *
     *  - `expression-statement ::= expression (";" | (?= "}"))
     *  - `assignment-statement ::= expression "=" expression ";"`
     */
    private fun parseStatement(): RhovasAst.Statement {
        return when {
            peek("{") -> parseAst { RhovasAst.Statement.Expression(parseBlockStatement()) }
            peek(listOf("this", "super"), listOf("(", "{")) -> parseInitializerStatement()
            peek(listOf("val", "var"), RhovasTokenType.IDENTIFIER) -> parseVariableDeclarationStatement()
            peek("func") -> parseFunctionDeclarationStatement()
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
            peek(listOf("virtual", "abstract", "struct", "class", "interface")) -> parseAst {
                RhovasAst.Statement.Component(parseComponent())
            }
            else -> parseAst {
                val expression = parseExpression()
                if (match("=")) {
                    val value = parseExpression()
                    requireSemicolon { "An assignment statement must be followed by a semicolon, as in `name = value;`." }
                    RhovasAst.Statement.Assignment(expression, value)
                } else {
                    require(match(";") || peek("}")) { error(
                        "Expected semicolon.",
                        "An expression statement must be followed by a semicolon or the end of an expression block, as in `expression;` or `{ expression }`.",
                    ) }
                    RhovasAst.Statement.Expression(expression)
                }
            }
        }
    }

    /**
     *  - `block := "{" statement* "}"`
     */
    private fun parseBlockStatement(): RhovasAst.Expression.Block = parseAst {
        require(peek("{")) { error(
            "Expected opening brace.",
            "A block statement must begin with an opening brace, as in `{ statement; }`."
        ) }
        val statements = parseSequence("{", null, "}") {
            val statement = parseStatement()
            require(statement !is RhovasAst.Statement.Expression || tokens[-1]!!.literal == ";") { error(
                "Expected semicolon.",
                "An expression statement must be followed by a semicolon within a statement block, as in `{ expression; }`.",
            ) }
            statement
        }!!
        RhovasAst.Expression.Block(statements, null)
    }

    private fun parseInitializerStatement(): RhovasAst.Statement.Initializer = parseAst {
        require(match(listOf("this", "super")))
        val name = tokens[-1]!!.literal
        val arguments = parseSequence("(", ",", ")") {
            val expression = parseExpression()
            require(peek(listOf(",", ")"))) { error(
                "Expected closing parenthesis or comma.",
                "An initializer argument must be followed by a closing parenthesis `)` or comma `,`, as in `this(argument)` or `super(x, y, z)`.",
            ) }
            expression
        }?.toMutableList() ?: mutableListOf()
        val initializer = if (peek("{")) {
            parsePrimaryExpression() as RhovasAst.Expression.Literal.Object
        } else null
        requireSemicolon { "An initializer statement must be followed by a semicolon, as in `init { field };`." }
        RhovasAst.Statement.Initializer(name, arguments, initializer)
    }

    /**
     *  - `variable = ("val" | "var") identifier (":" type)? ("=" expression)? ";"`
     */
    private fun parseVariableDeclarationStatement(): RhovasAst.Statement.Declaration.Variable = parseAst {
        require(match(listOf("val", "var"), RhovasTokenType.IDENTIFIER))
        val mutable = tokens[-2]!!.literal == "var"
        val name = tokens[-1]!!.literal
        val type = if (match(":")) parseType() else null
        val value = if (match("=")) parseExpression() else null
        requireSemicolon { "A variable declaration must be followed by a semicolon, as in `val name = value;` or `var name: Type;`." }
        RhovasAst.Statement.Declaration.Variable(mutable, name, type, value)
    }

    /**
     *  - `function = "func" operator? identifier generics? parameters returns? throws? statement`
     *     - `operator = "op" ("+" | "-" | "*" | "/" | "[]" | "[]=")`
     *     - `generics = "<" generic? ("," generic)* ","? ">"`
     *        - `generic = identifier (":" type)?`
     *     - `parameters = "(" (parameter ("," parameter)* ","?) ")"`
     *        - `parameter = identifier (":" type)?`
     *     - `returns = ":" type`
     *     - `throws = "throws" type ("," type)*`
     */
    private fun parseFunctionDeclarationStatement(): RhovasAst.Statement.Declaration.Function = parseAst {
        require(match("func"))
        val operator = if (peek("op") && !peek("op", "(")) {
            require(match("op"))
            when {
                match(listOf("+", "-", "*", "/")) -> tokens[-1]!!.literal
                match("[", "]") -> if (match("=")) "[]=" else "[]"
                else -> throw error(
                    "Invalid operator overload.",
                    "Operator overloading may only be used with +, -, *, /, [], and []=, as in `func op+ add() { ... }`.",
                )
            }
        } else null
        val name = parseIdentifier { "A function declaration requires a name after `func`, as in `func name() { ... }`." }
        val generics = parseSequence("<", ",", ">") {
            val name = parseIdentifier { "A function generic type declaration requires a name, as in `func name<T>() { ... }` or `func name<X, Y, Z>() { ... }`." }
            val type = if (match(":")) parseType() else null
            require(peek(listOf(",", ">"))) { error(
                "Expected closing angle bracket or comma.",
                "A function generic type declaration must be followed by a closing angle bracket `>` or comma `,`, as in `func name<T>() { ... }` or `func name<X, Y, Z>() { ... }`.",
            ) }
            Pair(name, type)
        } ?: listOf()
        require(peek("(")) { error(
            "Expected opening parenthesis.",
            "A function declaration requires parenthesis after the name, as in `func name() { ... }` or `func name(x, y, z) { ... }`.",
        ) }
        val parameters = parseSequence("(", ",", ")") {
            val name = parseIdentifier { "A function parameter requires a name, as in `func f(name: Type) { ... }`." }
            val type = if (match(":")) parseType() else null
            require(peek(listOf(",", ")"))) { error(
                "Expected closing parenthesis or comma.",
                "A function parameter must be followed by a closing parenthesis `)` or comma `,`, as in `func name() { ... }` or `func name(x, y, z) { ... }`.",
            ) }
            Pair(name, type)
        }!!
        val returns = if (match(":")) parseType() else null
        val throws = if (match("throws")) parseSequence(",", this::parseType) else listOf()
        val block = parseBlockStatement()
        RhovasAst.Statement.Declaration.Function(operator, name, generics, parameters, returns, throws, block)
    }

    /**
     *  - `if = "if" "(" expression ")" statement ("else" statement)`
     */
    private fun parseIfStatement(): RhovasAst.Statement.If = parseAst {
        require(match("if"))
        require(match("(")) { error(
            "Expected opening parenthesis.",
            "An if statement requires parenthesis around the condition, as in `if (condition) { ... }`.",
        ) }
        val condition = parseExpression()
        require(match(")")) { error(
            "Expected closing parenthesis.",
            "An if statement condition must be followed by a closing parenthesis, as in `if (condition) { ... }`.",
        ) }
        val thenBlock = parseBlockStatement()
        val elseBlock = if (match("else")) parseBlockStatement() else null
        RhovasAst.Statement.If(condition, thenBlock, elseBlock)
    }

    /**
     * Dispatches to conditional/structural match or throws a [ParseException].
     */
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

    /**
     *  - `conditional-match = "match" "{" (expression ":" statement)* ("else" expression? ":" statement)? "}"`
     */
    private fun parseConditionalMatch(): RhovasAst.Statement.Match.Conditional = parseAst {
        require(match("match", "{"))
        val cases = mutableListOf<Pair<RhovasAst.Expression, RhovasAst.Statement>>()
        var elseCase: Pair<RhovasAst.Expression?, RhovasAst.Statement>? = null
        while (!match("}")) parse {
            if (match("else")) {
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
            } else {
                val condition = parseExpression()
                require(match(":")) { error(
                    "Expected colon.",
                    "A match condition must be followed by a colon `:`, as in `match { condition: ... }`.",
                ) }
                val statement = parseStatement()
                cases.add(Pair(condition, statement))
            }
        }
        RhovasAst.Statement.Match.Conditional(cases, elseCase)
    }

    /**
     *  - `structural-match = "match" "(" expression ")" "{" (pattern ":" statement)* ("else" pattern? ":" statement)? "}"`
     */
    private fun parseStructuralMatch(): RhovasAst.Statement.Match.Structural = parseAst {
        require(match("match", "("))
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
        while (!match("}")) parse {
            if (match("else")) {
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
            } else {
                val condition = parsePattern()
                require(match(":")) { error(
                    "Expected colon.",
                    "A match pattern must be followed by a colon `:`, as in `match (argument) { condition: ... }`.",
                ) }
                val statement = parseStatement()
                cases.add(Pair(condition, statement))
            }
        }
        RhovasAst.Statement.Match.Structural(argument, cases, elseCase)
    }

    /**
     *  - `for = "for" "(" "val" identifier "in" expression ")" statement`
     */
    private fun parseForStatement(): RhovasAst.Statement.For = parseAst {
        require(match("for"))
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
        val body = parseBlockStatement()
        RhovasAst.Statement.For(name, iterable, body)
    }

    /**
     *  - `while = "while" "(" expression ")" statement`
     */
    private fun parseWhileStatement(): RhovasAst.Statement.While = parseAst {
        require(match("while"))
        require(match("(")) { error(
            "Expected opening parenthesis.",
            "A while loop requires parenthesis around the condition, as in `while (condition) { ... }`.",
        ) }
        val condition = parseExpression()
        require(match(")")) { error(
            "Expected closing parenthesis.",
            "A while loop requires parenthesis around the condition, as in `while (condition) { ... }`.",
        ) }
        val body = parseBlockStatement()
        RhovasAst.Statement.While(condition, body)
    }

    /**
     *  - `try = "try" statement ("catch" "(" "val" identifier ":" type ")" statement)* ("finally" statement)?`
     */
    private fun parseTryStatement(): RhovasAst.Statement.Try = parseAst {
        require(match("try"))
        val body = parseBlockStatement()
        val catches = mutableListOf<RhovasAst.Statement.Try.Catch>()
        while (match("catch")) {
            catches.add(parseAst(tokens[-1]!!.range) {
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
                val body = parseBlockStatement()
                RhovasAst.Statement.Try.Catch(name, type, body)
            })
        }
        val finallyBlock = if (match("finally")) parseBlockStatement() else null
        RhovasAst.Statement.Try(body, catches, finallyBlock)
    }

    /**
     *  - `with = "with" "(" ("val" identifier "=")? expression ")" statement`
     */
    private fun parseWithStatement(): RhovasAst.Statement.With = parseAst {
        require(match("with"))
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
        val body = parseBlockStatement()
        RhovasAst.Statement.With(name, argument, body)
    }

    /**
     *  - `label = identifier ":" statement`
     */
    private fun parseLabelStatement(): RhovasAst.Statement.Label = parseAst {
        require(match(RhovasTokenType.IDENTIFIER, ":"))
        val label = tokens[-2]!!.literal
        val statement = parseStatement()
        RhovasAst.Statement.Label(label, statement)
    }

    /**
     *  - `break = "break" identifier? ";"`
     */
    private fun parseBreakStatement(): RhovasAst.Statement.Break = parseAst {
        require(match("break"))
        val label = if (match(RhovasTokenType.IDENTIFIER)) tokens[-1]!!.literal else null
        requireSemicolon { "A break statement must be followed by a semicolon, as in `break;` or `break label;`." }
        RhovasAst.Statement.Break(label)
    }

    /**
     *  - `continue = "continue" identifier? ";"`
     */
    private fun parseContinueStatement(): RhovasAst.Statement.Continue = parseAst {
        require(match("continue"))
        val label = if (match(RhovasTokenType.IDENTIFIER)) tokens[-1]!!.literal else null
        requireSemicolon { "A continue statement must be followed by a semicolon, as in `continue;` or `continue label;`." }
        RhovasAst.Statement.Continue(label)
    }

    /**
     *  - `return = "return" expression? ";"`
     */
    private fun parseReturnStatement(): RhovasAst.Statement.Return = parseAst {
        require(match("return"))
        val value = if (!peek(";")) parseExpression() else null
        requireSemicolon { "A return statement must be followed by a semicolon, as in `return;` or `return value;`." }
        RhovasAst.Statement.Return(value)
    }

    /**
     *  - `throw = "throw" expression? ";"`
     */
    private fun parseThrowStatement(): RhovasAst.Statement.Throw = parseAst {
        require(match("throw"))
        val exception = parseExpression()
        requireSemicolon { "A throw statement must be followed by a semicolon, as in `throw exception;`." }
        RhovasAst.Statement.Throw(exception)
    }

    /**
     *  - `assert = "assert" expression (":" expression)? ";"`
     */
    private fun parseAssertStatement(): RhovasAst.Statement.Assert = parseAst {
        require(match("assert"))
        val condition = parseExpression()
        val message = if (match(":")) parseExpression() else null
        requireSemicolon { "An assert statement must be followed by a semicolon, as in `assert condition;`." }
        RhovasAst.Statement.Assert(condition, message)
    }

    /**
     *  - `require = "require" expression (":" expression)? ";"`
     */
    private fun parseRequireStatement(): RhovasAst.Statement.Require = parseAst {
        require(match("require"))
        val condition = parseExpression()
        val message = if (match(":")) parseExpression() else null
        requireSemicolon { "A require statement must be followed by a semicolon, as in `require condition;`." }
        RhovasAst.Statement.Require(condition, message)
    }

    /**
     *  - `ensure = "ensure" expression (":" expression)? ";"`
     */
    private fun parseEnsureStatement(): RhovasAst.Statement.Ensure = parseAst {
        require(match("ensure"))
        val condition = parseExpression()
        val message = if (match(":")) parseExpression() else null
        requireSemicolon { "An ensure statement must be followed by a semicolon, as in `ensure condition;`." }
        RhovasAst.Statement.Ensure(condition, message)
    }

    private fun parseExpression(): RhovasAst.Expression {
        return parseLogicalOrExpression()
    }

    /**
     *  - `logical-or = logical-and ("||" logical-and)`
     */
    private fun parseLogicalOrExpression(): RhovasAst.Expression {
        return parseBinaryExpression(::parseLogicalAndExpression, "||")
    }

    /**
     *  - `logical-and = equality ("&&" equality)`
     */
    private fun parseLogicalAndExpression(): RhovasAst.Expression {
        return parseBinaryExpression(::parseEqualityExpression, "&&")
    }

    /**
     *  - `equality = comparison (("==" | "!=" | "===" | "!==") comparison)*`
     */
    private fun parseEqualityExpression(): RhovasAst.Expression {
        return parseBinaryExpression(::parseComparisonExpression, "==", "!=", "===", "!==")
    }

    /**
     *  - `comparison = additive (("<" | ">" | "<=" | ">=") additive)*`
     */
    private fun parseComparisonExpression(): RhovasAst.Expression {
        return parseBinaryExpression(::parseAdditiveExpression, "<", ">", "<=", ">=")
    }

    /**
     *  - `additive = multiplicative (("+" | "-") multiplicative)*`
     */
    private fun parseAdditiveExpression(): RhovasAst.Expression {
        return parseBinaryExpression(::parseMultiplicativeExpression, "+", "-")
    }

    /**
     *  - `multiplicative = unary (("*" | "/") unary)*`
     */
    private fun parseMultiplicativeExpression(): RhovasAst.Expression {
        return parseBinaryExpression(::parseUnaryExpression, "*", "/")
    }

    /**
     * Parses a binary expression with the given [operators] by matching on the
     * longest ([String.lastOrNull]) available operator (operators must be
     * passed in the correct order, with shortest at the front).
     */
    private fun parseBinaryExpression(parser: () -> RhovasAst.Expression, vararg operators: String): RhovasAst.Expression {
        var expression = parser()
        context.addLast(expression.context.first())
        while (true) {
            context.addLast(expression.context.first())
            val operator = operators.lastOrNull { o ->
                match(*o.toCharArray().map { it.toString() }.toTypedArray())
            } ?: break
            expression = parseAst(tokens[-1]!!.range) {
                val right = parser()
                RhovasAst.Expression.Binary(operator, expression, right)
            }
            context.removeLast()
        }
        context.removeLast()
        context.removeLast()
        return expression
    }

    /**
     *  - `unary = ("-" | "!") unary | secondary`
     */
    private fun parseUnaryExpression(): RhovasAst.Expression {
        return if (match(listOf("-", "!"))) parseAst {
            val operator = tokens[-1]!!.literal
            val expression = parseUnaryExpression()
            RhovasAst.Expression.Unary(operator, expression)
        } else {
            parseSecondaryExpression()
        }
    }

    /**
     *  - `secondary = primary (pipeline | method | property | index)*`
     *     - `pipeline = "?"? "." "."? "|" (type ".")? identifier invoke-arguments`
     *     - `method = "?"? "." "."? identifier invoke-arguments`
     *     - `property = "?"? "." identifier`
     *     - `index = "[" expression* "]"`
     */
    private fun parseSecondaryExpression(): RhovasAst.Expression {
        var expression = parsePrimaryExpression()
        context.addLast(expression.context.first())
        while (true) {
            context.addLast(expression.context.first())
            expression = when {
                peek(".") || peek("?", ".") -> parseAst {
                    val coalesce = match("?")
                    require(match("."))
                    val cascade = match(".")
                    if (match("|")) {
                        val qualifier = if (peek(RhovasTokenType.IDENTIFIER) && tokens[0]!!.literal[0].isUpperCase() && tokens[1]?.literal == ".") {
                            val qualifier = parseType()
                            require(match(".")) { error(
                                "Expected period.",
                                "A pipeline qualifier must be followed by a period, as in `receiver.|Qualifier.function()`."
                            ) }
                            qualifier
                        } else null
                        val name = parseIdentifier { "A pipeline expression requires a name, as in `receiver.|function()` or `receiver.|Qualifier.function()`." }
                        require(peek(listOf("(", "|", "{"))) { error(
                            "Expected opening parenthesis, pipe, or brace.",
                            "A pipeline expression requires an invocation, as in `receiver.|function()`, `receiver.|function |name| { ... }`, or `receiver.|function { ... }`.",
                        ) }
                        val arguments = parseInvokeExpressionArguments()
                        RhovasAst.Expression.Invoke.Pipeline(expression, coalesce, cascade, qualifier, name, arguments)
                    } else if (match(RhovasTokenType.INTEGER)) {
                        val name = tokens[-1]!!.literal
                        require(!cascade) { error(
                            "Invalid element cascade.",
                            "An element access cannot be combined with the cascade operator, as in `receiver..0`.",
                        ) }
                        RhovasAst.Expression.Access.Property(expression, coalesce, name)
                    } else {
                        val name = parseIdentifier { "A property or method expression requires a name, as in `receiver.property` or `receiver.method()`." }
                        if (peek(listOf("(", "|", "{"))) {
                            val arguments = parseInvokeExpressionArguments()
                            RhovasAst.Expression.Invoke.Method(expression, coalesce, cascade, name, arguments)
                        } else {
                            require(!cascade) { error(
                                "Invalid property cascade.",
                                "A property access cannot be combined with the cascade operator, as in `receiver..property`.",
                            ) }
                            RhovasAst.Expression.Access.Property(expression, coalesce, name)
                        }
                    }
                }
                peek("[") || peek("?", "[") -> parseAst {
                    val coalesce = match("?")
                    val arguments = parseSequence("[", ",", "]") {
                        val expression = parseExpression()
                        require(peek(listOf(",", "]"))) { error(
                            "Expected closing bracket or comma.",
                            "An index expression must be followed by a closing bracket `]` or comma `,`, as in `expression[index]` or `expression[x, y, z]`.",
                        ) }
                        expression
                    }!!
                    RhovasAst.Expression.Access.Index(expression, coalesce, arguments)
                }
                else -> break
            }
            context.removeLast()
        }
        context.removeLast()
        context.removeLast()
        return expression
    }

    /**
     *  - `primary = literal | group | constructor | variable | function | macro`
     *     - `literal = "null" | "true" | "false" | integer | decimal | string | atom | list | object | type`
     *        - `string = "\"" (lexer-string | "${" expression "}")* "\""`
     *        - `atom = ":" identifier`
     *        - `list = "[" (expression ("," expression)* ","?)? "]"`
     *        - `object = "{" (property ("," property)* ","?)? "}"`
     *           - `property = identifier (":" expression)?`
     *     - `constructor = type invoke-arguments`
     *     - `variable = (type ".")? identifier`
     *     - `function = (type ".")? identifier invoke-arguments`
     */
    private fun parsePrimaryExpression(): RhovasAst.Expression {
        return when {
            match("do") -> parseAst(tokens[-1]!!.range) {
                parseBlockExpression()
            }
            match("null") -> parseAst(tokens[-1]!!.range) {
                RhovasAst.Expression.Literal.Scalar(null)
            }
            match(listOf("true", "false")) -> parseAst(tokens[-1]!!.range) {
                RhovasAst.Expression.Literal.Scalar(tokens[-1]!!.literal.toBooleanStrict())
            }
            match(listOf(RhovasTokenType.INTEGER, RhovasTokenType.DECIMAL)) -> parseAst(tokens[-1]!!.range) {
                RhovasAst.Expression.Literal.Scalar(tokens[-1]!!.value)
            }
            match("\"") -> parseAst(tokens[-1]!!.range) {
                lexer.mode = "string"
                val literals = mutableListOf<String>()
                val arguments = mutableListOf<RhovasAst.Expression>()
                if (!peek(RhovasTokenType.STRING)) {
                    literals.add("")
                }
                while (!match("\"")) {
                    if (match("\${")) parse(tokens[-1]!!.range) {
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
                    } else {
                        require(match(RhovasTokenType.STRING)) { error(
                            "Unterminated string literal.",
                            "A string literal must end with a double quote (\") and cannot span multiple lines.",
                        ) }
                        literals.add(tokens[-1]!!.value as String)
                    }
                }
                lexer.mode = ""
                RhovasAst.Expression.Literal.String(literals, arguments)
            }
            match(RhovasTokenType.ATOM) -> parseAst(tokens[-1]!!.range) {
                RhovasAst.Expression.Literal.Scalar(tokens[-1]!!.value)
            }
            peek("[") -> parseAst {
                val elements = parseSequence("[", ",", "]") {
                    val expression = parseExpression()
                    require(peek(listOf(",", "]"))) { error(
                        "Expected closing bracket or comma.",
                        "A list literal element must be followed by a closing bracket `]` or comma `,`, as in `[element]` or `[x, y, z]`.",
                    ) }
                    expression
                }!!
                RhovasAst.Expression.Literal.List(elements)
            }
            peek("{") -> parseAst {
                val properties = parseSequence("{", ",", "}") {
                    val key = parseIdentifier { "An object literal entry requires a key, as in `{key: value}` or `{x, y, z}`." }
                    val value = if (match(":")) parseExpression() else parseAst(tokens[-1]!!.range) { RhovasAst.Expression.Access.Variable(null, key) }
                    require(peek(listOf(",", "}"))) { error(
                        "Expected closing parenthesis or comma.",
                        "An object literal entry must be followed by a closing parenthesis `}` or comma `,`, as in `{key: value}` or `{x, y, z}`.",
                    ) }
                    Pair(key, value)
                }!!
                RhovasAst.Expression.Literal.Object(properties)
            }
            match("(") -> parseAst(tokens[-1]!!.range) {
                val expression = parseExpression()
                require(match(")")) { error(
                    "Expected closing parenthesis.",
                    "A group expression must be followed by a closing parenthesis, as in `(expression)`.",
                ) }
                RhovasAst.Expression.Group(expression)
            }
            peek(RhovasTokenType.IDENTIFIER) && tokens[0]!!.literal[0].isUpperCase() -> parseAst {
                val type = parseType()
                val name = if (match(".", RhovasTokenType.IDENTIFIER)) tokens[-1]!!.literal else null
                val arguments = if (peek(listOf("(", "{")) || peek("|", RhovasTokenType.IDENTIFIER)) parse(tokens[-1]!!.range) { parseInvokeExpressionArguments() } else null
                when {
                    name == null && arguments == null -> RhovasAst.Expression.Literal.Type(type)
                    name == null -> RhovasAst.Expression.Invoke.Constructor(type, arguments!!)
                    arguments == null -> RhovasAst.Expression.Access.Variable(type, name)
                    else -> RhovasAst.Expression.Invoke.Function(type, name, arguments)
                }
            }
            match(RhovasTokenType.IDENTIFIER) -> parseAst(tokens[-1]!!.range) {
                val name = tokens[-1]!!.literal
                val arguments = if (peek(listOf("(", "{")) || peek("|", RhovasTokenType.IDENTIFIER)) parseInvokeExpressionArguments() else null
                when (arguments) {
                    null -> RhovasAst.Expression.Access.Variable(null, name)
                    else -> RhovasAst.Expression.Invoke.Function(null, name, arguments)
                }
            }
            peek("#", RhovasTokenType.IDENTIFIER) -> parseMacroExpression()
            else -> throw error(
                "Expected expression.",
                "An expression must start with a literal (Null/Boolean/Integer/Decimal/String/Atom/List/Object), an opening parenthesis `(` for a group, an identifier for a variable/function, or a hashtag `#` followed by an identifier for a macro."
            )
        }
    }

    private fun parseBlockExpression(): RhovasAst.Expression.Block = parseAst {
        require(match("{"))
        val statements = mutableListOf<RhovasAst.Statement>()
        var expression: RhovasAst.Expression? = null
        while (!match("}")) {
            val statement = parseStatement()
            if (statement is RhovasAst.Statement.Expression && tokens[-1]!!.literal != ";") {
                expression = statement.expression
                require(peek("}"))
            } else {
                statements.add(statement)
            }
        }
        RhovasAst.Expression.Block(statements, expression)
    }

    /**
     *  - `invoke-arguments = arguments lambda? | lambda`
     *     - `arguments = "(" (expression ("," expression)* ","?)? ")"`
     *     - `lambda = ("|" parameter ("," parameter)* ","? "|")? block`
     *        - `parameter = identifier (":" type)?`
     */
    private fun parseInvokeExpressionArguments(): List<RhovasAst.Expression> {
        require(peek(listOf("(", "|", "{")))
        val arguments = parseSequence("(", ",", ")") {
            val expression = parseExpression()
            require(peek(listOf(",", ")"))) { error(
                "Expected closing parenthesis or comma.",
                "A function argument must be followed by a closing parenthesis `)` or comma `,`, as in `function(argument)` or `function(x, y, z)`.",
            ) }
            expression
        }?.toMutableList() ?: mutableListOf()
        if (peek("|", RhovasTokenType.IDENTIFIER) || peek("{")) {
            arguments.add(parseAst {
                val parameters = parseSequence("|", ",", "|") {
                    val name = parseIdentifier { "A lambda parameter requires a name, as in `lambda |name| { ... }`." }
                    val type = if (match(":")) parseType() else null
                    require(peek(listOf(",", "|"))) { error(
                        "Expected closing pipe or comma.",
                        "A lambda parameter must be followed by a closing pipe `|` or comma `,`, as in `lambda |parameter| { ... }` or `lambda |x, y, z| { ... }`.",
                    ) }
                    Pair(name, type)
                } ?: listOf()
                require(peek("{")) { error(
                    "Expected opening brace.",
                    "A lambda's parameters must be followed by an opening brace `{`, as in `lambda |parameter| { ... }"
                ) }
                val body = parseBlockExpression()
                RhovasAst.Expression.Lambda(parameters, body)
            })
        }
        return arguments
    }

    /**
     *  - `macro = "#" identifier (arguments dsl? | dsl)`
     *     - `arguments = "(" (expression ("," expression)* ","?)? ")"`
     *     - `dsl = "{" dsl-grammar "}"`
     */
    private fun parseMacroExpression(): RhovasAst.Expression = parseAst {
        require(match("#", RhovasTokenType.IDENTIFIER))
        val name = tokens[-1]!!.literal
        require(peek(listOf("(", "{"))) { error(
            "Expected opening parenthesis or brace.",
        "A macro expression must be followed by an opening parenthesis `(` or brace `{`, as in `#macro()` or `#dsl { ... }`.",
        ) }
        val arguments = parseSequence("(", ",", ")") {
            val expression = parseExpression()
            require(peek(listOf(",", ")"))) { error(
                "Expected closing parenthesis or comma.",
                "An macro argument must be followed by a closing parenthesis `)` or comma `,`, as in `function(argument)` or `function(x, y, z)`.",
            ) }
            expression
        } ?: listOf()
        val dsl = if (match("{")) parse(tokens[-1]!!.range) {
            val parser = DslParser(lexer.input)
            parser.lexer.state = lexer.state.let {
                Pair(it.first.copy(index = it.first.index - 1, column = it.first.column - 1, length = 0), it.second)
            }
            val ast = parser.parse("source") as DslAst.Source
            lexer.state = parser.lexer.state.let {
                Pair(it.first.copy(index = it.first.index - 1, column = it.first.column - 1, length = 0), it.second)
            }
            require(match("}"))
            ast
        } else null
        RhovasAst.Expression.Invoke.Macro(name, arguments, dsl)
    }

    /**
     *  - `pattern = (value | variable | ordered-destructure | named-destructure | typed-destructure) varargs? predicate?`
     *     - `value = "null" | "true" | "false" | integer | decimal | string | atom | "$" "{" expression "}"`
     *     - `variable = identifier` (where `identifier` is lowercase)`
     *     - `ordered-destructure = "[" (pattern ("," pattern)*)? "]"`
     *     - `named-destructure = "{" ((identifier ":")? pattern ("," (identifier ":")? pattern)*)? "}"`
     *     - `typed-destructure = identifier pattern?` (where `identifier` is Uppercase)`
     *     - `varargs = "+" | "*"`
     *     - `predicate = "$" "{" expression "}"`
     */
    private fun parsePattern(): RhovasAst.Pattern {
        var pattern = when {
            peek(listOf("null", "true", "false", RhovasTokenType.INTEGER, RhovasTokenType.DECIMAL, "\"", RhovasTokenType.ATOM)) -> parseAst {
                RhovasAst.Pattern.Value(parsePrimaryExpression())
            }
            peek(RhovasTokenType.IDENTIFIER) -> parseAst {
                if (tokens[0]!!.literal[0].isUpperCase()) {
                    val type = parseType()
                    val pattern = if (peek(listOf(RhovasTokenType.IDENTIFIER, "[", "{", "$"))) parsePattern() else null
                    RhovasAst.Pattern.TypedDestructure(type, pattern)
                } else {
                    require(match(RhovasTokenType.IDENTIFIER))
                    val name = tokens[-1]!!.literal
                    RhovasAst.Pattern.Variable(name)
                }
            }
            peek("[") -> parseAst {
                val patterns = parseSequence("[", ",", "]") {
                    val pattern = parsePattern()
                    require(peek(listOf(",", "]"))) { error(
                        "Expected closing bracket or comma.",
                        "An ordered destructuring element must be followed by a closing bracket `]` or comma `,`, as in `[argument]` or `[x, y, z]`.",
                    ) }
                    pattern
                }!!
                RhovasAst.Pattern.OrderedDestructure(patterns)
            }
            peek("{") -> parseAst {
                val patterns = parseSequence("{", ",", "}") {
                    val key = if (match(RhovasTokenType.IDENTIFIER, ":")) tokens[-2]!!.literal else null
                    val pattern = parsePattern()
                    require(peek(listOf(",", "}"))) { error(
                        "Expected closing brace or comma.",
                        "A named destructuring entry must be followed by a closing brace `}` or comma `,`, as in `{key: value}` or `{x, y, z}`.",
                    ) }
                    Pair(key, pattern)
                }!!
                RhovasAst.Pattern.NamedDestructure(patterns)
            }
            peek(listOf("*", "+")) -> null
            match("$") -> parseAst(tokens[-1]!!.range) {
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
            pattern = RhovasAst.Pattern.VarargDestructure(pattern, operator).also {
                it.context = listOfNotNull(pattern?.context?.first(), tokens[-1]!!.range)
            }
        }
        if (match("$")) parse(tokens[-1]!!.range) {
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

    /**
     *  - `type = identifier ("." identifier)* ("<" (generic-type ("," generic-type)* ","?)? ">")? "?"?`
     *     - `generic-type = tuple | struct | variant-able`
     *     - `tuple = "[" (type ("," type)* ","?)? "]"`
     *     - `struct = "{" (identifier ":" type ("," identifier ":" type)* ","?)? "}"`
     *     - `variant-able = type (":" "*")? | "*" (":" type)?`
     */
    private fun parseType(): RhovasAst.Type = parseAst {
        val path = mutableListOf<String>()
        path.add(parseIdentifier { "A type requires a name, as in `Type`." })
        while (peek(".", RhovasTokenType.IDENTIFIER) && tokens[1]!!.literal.first().isUpperCase()) {
            require(match(".", RhovasTokenType.IDENTIFIER))
            path.add(tokens[-1]!!.literal)
        }
        val generics = parseSequence("<", ",", ">") {
            val type = when {
                peek("[") -> {
                    val elements = parseSequence("[", ",", "]") {
                        val type = parseType()
                        require(peek(listOf(",", "]"))) { error(
                            "Expected closing bracket or comma.",
                            "A tuple type element must be followed by a closing bracket `]` or comma `,`, as in `[Type]` or `[X, Y, Z]`.",
                        ) }
                        type
                    }!!
                    RhovasAst.Type.Tuple(elements)
                }
                peek("{") -> {
                    val fields = parseSequence("{", ",", "}") {
                        val name = parseIdentifier { "A struct type field requires a name, as in `{name: Type}`." }
                        require(match(":")) { error(
                            "Expected colon",
                            "A struct field requires a colon between the name and type, as in `{name: Type}`.",
                        ) }
                        val type = parseType()
                        require(peek(listOf(",", "}"))) { error(
                            "Expected closing brace or comma.",
                            "An struct type field must be followed by a closing brace `}` or comma `,`, as in `{name: Type}` or `{x: X, y: Y, z: Z}`.",
                        ) }
                        Pair(name, type)
                    }!!
                    RhovasAst.Type.Struct(fields)
                }
                else -> {
                    var type = if (match("*")) null else parseType()
                    if (match(":")) {
                        val upper = if (match("*")) null else parseType()
                        type = RhovasAst.Type.Variant(type, upper)
                    }
                    type ?: RhovasAst.Type.Variant(null, null)
                }
            }
            require(peek(listOf(",", ">"))) { error(
                "Expected closing bracket or comma.",
                "A generic parameter must be followed by a closing bracket `>` or comma `,`, as in `Type<T>` or `Type<X, Y, Z>`",
            ) }
            type
        }
        val nullable = match("?")
        RhovasAst.Type.Reference(path, generics, nullable)
    }

    /**
     *  - `interpolation = "$" "{" expression "}"`
     */
    private fun parseInterpolation(): RhovasAst.Expression = parseAst {
        require(match("$", "{"))
        val expression = parseExpression()
        require(match("}")) { error(
            "Expected closing brace.",
            "An interpolated value requires braces around the expression, as in `\${value}`.",
        ) }
        expression
    }

    /**
     * Helper for matching an identifier or throwing a [ParseException].
     */
    private fun parseIdentifier(details: () -> String): String {
        require(match(RhovasTokenType.IDENTIFIER)) { error("Expected identifier.", details()) }
        return tokens[-1]!!.literal
    }

    /**
     * Helper for parsing a sequence with a separator.
     *
     *  - sequence := parser (separator parser)*
     */
    private fun <T> parseSequence(separator: String, parser: () -> T): List<T> {
        val sequence = mutableListOf<T>()
        do parse {
            sequence.add(parser())
        } while (match(separator))
        return sequence
    }

    /**
     * Helper for parsing a sequence with start/end markers. The caller is
     * responsible for validating separator logic, as well as any additional
     * requirements for optional/empty sequences.
     *
     *  - sequence := (start (parser separator)* end)?
     */
    private fun <T> parseSequence(start: String, separator: String?, end: String, parser: () -> T): List<T>? {
        return if (match(start)) parse {
            val sequence = mutableListOf<T>()
            while (!match(end)) {
                sequence.add(parser())
                require(separator == null || match(separator) || peek(end))
            }
            sequence
        } else null
    }

    /**
     * Helper for matching a semicolon or throwing a [ParseException].
     */
    private fun requireSemicolon(details: () -> String) {
        require(match(";")) { error("Expected semicolon.", details()) }
    }

    private fun <T> parse(first: Input.Range = (tokens[0] ?: tokens[-1]!!).range, parser: () -> T): T {
        context.addLast(first)
        return parser().also { context.removeLast() }
    }

    private fun <T: RhovasAst> parseAst(first: Input.Range = (tokens[0] ?: tokens[-1]!!).range, parser: () -> T): T {
        return parse(first, parser).also {
            it.context = listOfNotNull(first, tokens[-1]!!.range.takeIf { it != first })
        }
    }

}
