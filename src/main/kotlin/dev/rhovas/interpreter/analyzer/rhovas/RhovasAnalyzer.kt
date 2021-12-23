package dev.rhovas.interpreter.analyzer.rhovas

import dev.rhovas.interpreter.analyzer.Analyzer
import dev.rhovas.interpreter.environment.Function
import dev.rhovas.interpreter.environment.Method
import dev.rhovas.interpreter.environment.Scope
import dev.rhovas.interpreter.environment.Variable
import dev.rhovas.interpreter.library.Library
import dev.rhovas.interpreter.parser.rhovas.RhovasAst
import java.lang.AssertionError
import java.math.BigDecimal
import java.math.BigInteger

class RhovasAnalyzer(scope: Scope) : Analyzer(scope), RhovasAst.Visitor<RhovasIr> {

    override fun visit(ast: RhovasAst.Source): RhovasIr.Source {
        val statements = ast.statements.map { visit(it) }
        return RhovasIr.Source(statements)
    }

    private fun visit(ast: RhovasAst.Statement): RhovasIr.Statement {
        return super.visit(ast) as RhovasIr.Statement
    }

    override fun visit(ast: RhovasAst.Statement.Block): RhovasIr {
        return scoped(Scope(scope)) {
            val statements = ast.statements.map { visit(it) }
            RhovasIr.Statement.Block(statements)
        }
    }

    override fun visit(ast: RhovasAst.Statement.Expression): RhovasIr {
        require(ast.expression is RhovasAst.Expression.Invoke) { error(
            ast.expression,
            "Invalid expression statement.",
            "An expression statement requires an invoke expression in order to perform a useful side-effect, but received ${ast.expression.javaClass.name}.",
        ) }
        val expression = visit(ast.expression) as RhovasIr.Expression.Invoke
        return RhovasIr.Statement.Expression(expression)
    }

    override fun visit(ast: RhovasAst.Statement.Function): RhovasIr.Statement.Function {
        require(!scope.functions.isDefined(ast.name, ast.parameters.size, true)) { error(
            ast,
            "Redefined function.",
            "The function ${ast.name}/${ast.parameters.size} is already defined in this scope.",
        ) }
        val parameters = ast.parameters.map { Pair(it.first, it.second?.let { visit(it).type } ?: Library.TYPES["Any"]!!) }
        val returns = ast.returns?.let { visit(it).type } ?: Library.TYPES["Void"]!!
        val function = Function(ast.name, parameters, returns)
        scope.functions.define(function)
        //TODO: Validate thrown exceptions
        val body = scoped(Scope(scope)) {
            parameters.forEach { scope.variables.define(Variable(it.first, it.second)) }
            visit(ast.body) as RhovasIr.Statement.Block
        }
        return RhovasIr.Statement.Function(function, body)
    }

    override fun visit(ast: RhovasAst.Statement.Declaration): RhovasIr.Statement.Declaration {
        require(!scope.variables.isDefined(ast.name, true)) { error(
            ast,
            "Redefined variable.",
            "The variable ${ast.name} is already defined in this scope.",
        ) }
        require(ast.type != null || ast.value != null) { error(
            ast,
            "Undefined variable type.",
            "A variable declaration requires either a type or an initial value.",
        ) }
        val type = ast.type?.let { visit(it) }
        val value = ast.value?.let { visit(it) }
        val variable = Variable(ast.name, type?.type ?: value!!.type)
        scope.variables.define(variable)
        return RhovasIr.Statement.Declaration(variable, value)
    }

    override fun visit(ast: RhovasAst.Statement.Assignment): RhovasIr.Statement.Assignment {
        require(ast.receiver is RhovasAst.Expression.Access) { error(
            ast.receiver,
            "Invalid assignment receiver.",
            "An assignment statement requires the receiver to be an access expression, but received ${ast.receiver.javaClass.name}.",
        ) }
        return when (ast.receiver) {
            is RhovasAst.Expression.Access.Variable -> {
                val variable = scope.variables[ast.receiver.name] ?: throw error(
                    ast,
                    "Undefined variable.",
                    "The variable ${ast.receiver.name} is not defined in the current scope.",
                )
                //TODO: Require mutable variable
                val value = visit(ast.value)
                require(value.type.isSubtypeOf(variable.type)) { error(
                    ast.value,
                    "Invalid assignment value type.",
                    "The variable ${variable.name} requires the value to be type ${variable.type.name}, but received ${value.type.name}.",
                ) }
                RhovasIr.Statement.Assignment.Variable(variable, value)
            }
            is RhovasAst.Expression.Access.Property -> {
                require(!ast.receiver.coalesce) { error(
                    ast.receiver,
                    "Invalid assignment receiver.",
                    "An assignment statement requires the receiver property access to be non-coalescing.",
                ) }
                val receiver = visit(ast.receiver)
                val method = receiver.type.methods[ast.receiver.name, 2]?.let { Method(it) } ?: throw error(
                    ast,
                    "Undefined property.",
                    "The property setter ${receiver.type.name}.${ast.receiver.name}/1 is not defined.",
                )
                //TODO: Require mutable variable
                val value = visit(ast.value)
                require(value.type.isSubtypeOf(receiver.method.parameters[0].second)) { error(
                    ast.value,
                    "Invalid assignment value type.",
                    "The property ${receiver.method.name} requires the value to be type ${receiver.method.parameters[0].second.name}, but received ${value.type.name}.",
                ) }
                RhovasIr.Statement.Assignment.Property(receiver, method, value)
            }
            is RhovasAst.Expression.Access.Index -> {
                val receiver = visit(ast.receiver.receiver)
                val method = receiver.type.methods["[]=", 1 + ast.receiver.arguments.size + 1]?.let { Method(it) } ?: throw error(
                    ast,
                    "Undefined method.",
                    "The method ${receiver.type.name}.[]=/${ast.receiver.arguments.size + 1} is not defined.",
                )
                val arguments = ast.receiver.arguments.map { visit(it) }
                for (i in arguments.indices) {
                    require(arguments[i].type.isSubtypeOf(method.parameters[i].second)) { throw error(
                        ast.receiver.arguments[i],
                        "Invalid method argument type.",
                        "The method ${receiver.type.name}.[]=/${ast.receiver.arguments.size + 1} requires argument ${i} to be type ${method.parameters[i].second.name}, but received ${arguments[i].type.name}",
                    ) }
                }
                val value = visit(ast.value)
                require(value.type.isSubtypeOf(method.returns)) { error(
                    ast.value,
                    "Invalid assignment value type.",
                    "The method ${receiver.type.name}.[]=/${ast.receiver.arguments.size + 1} requires the value to be type ${method.parameters.last().second.name}, but received ${value.type.name}.",
                ) }
                RhovasIr.Statement.Assignment.Index(method, receiver, arguments, value)
            }
            else -> throw AssertionError()
        }
    }

    override fun visit(ast: RhovasAst.Statement.If): RhovasIr.Statement.If {
        val condition = visit(ast.condition)
        require(condition.type.isSubtypeOf(Library.TYPES["Boolean"]!!)) { error(
            ast.condition,
            "Invalid if condition type.",
            "An if statement requires the condition to be type Boolean, but received ${condition.type.name}.",
        ) }
        val thenStatement = visit(ast.thenStatement)
        val elseStatement = ast.elseStatement?.let { visit(it) }
        return RhovasIr.Statement.If(condition, thenStatement, elseStatement)
    }

    override fun visit(ast: RhovasAst.Statement.Match.Conditional): RhovasIr {
        fun visitCondition(ast: RhovasAst.Expression): RhovasIr.Expression {
            val condition = visit(ast)
            require(condition.type.isSubtypeOf(Library.TYPES["Boolean"]!!)) { error(
                ast,
                "Invalid match condition type.",
                "A conditional match statement requires the condition to be type Boolean, but received ${condition.type.name}.",
            ) }
            return condition
        }
        val cases = ast.cases.map {
            val condition = visitCondition(it.first)
            val statement = visit(it.second)
            Pair(condition, statement)
        }
        val elseCase = ast.elseCase?.let {
            val condition = it.first?.let { visitCondition(it) }
            val statement = visit(it.second)
            Pair(condition, statement)
        }
        return RhovasIr.Statement.Match.Conditional(cases, elseCase)
    }

    override fun visit(ast: RhovasAst.Statement.Match.Structural): RhovasIr {
        val argument = visit(ast.argument)
        //TODO: Typecheck patterns
        val cases = ast.cases.map {
            scoped(Scope(scope)) {
                val pattern = visit(it.first)
                val statement = visit(it.second)
                Pair(pattern, statement)
            }
        }
        val elseCase = ast.elseCase?.let {
            scoped(Scope(scope)) {
                val pattern = it.first?.let { visit(it) }
                val statement = visit(it.second)
                Pair(pattern, statement)
            }
        }
        return RhovasIr.Statement.Match.Structural(argument, cases, elseCase)
    }

    override fun visit(ast: RhovasAst.Statement.For): RhovasIr.Statement.For {
        val argument = visit(ast.argument)
        //TODO: Iterable type
        require(argument.type.isSubtypeOf(Library.TYPES["List"]!!)) { error(
            ast.argument,
            "Invalid for loop argument type.",
            "A for loop requires the argument to be type List, but received ${argument.type.name}.",
        ) }
        return scoped(Scope(scope)) {
            //TODO: Generic types
            scope.variables.define(Variable(ast.name, Library.TYPES["Any"]!!))
            val body = visit(ast.body)
            RhovasIr.Statement.For(ast.name, argument, body)
        }
    }

    override fun visit(ast: RhovasAst.Statement.While): RhovasIr.Statement.While {
        val condition = visit(ast.condition)
        require(condition.type.isSubtypeOf(Library.TYPES["Boolean"]!!)) { error(
            ast.condition,
            "Invalid while condition type.",
            "An while statement requires the condition to be type Boolean, but received ${condition.type.name}.",
        ) }
        val body = visit(ast.body)
        return RhovasIr.Statement.While(condition, body)
    }

    override fun visit(ast: RhovasAst.Statement.Try): RhovasIr.Statement.Try {
        val body = visit(ast.body)
        //TODO: Validate thrown exceptions
        val catches = ast.catches.map { visit(it) }
        //TODO: Validate special control flow (and spec)
        val finallyStatement = ast.finallyStatement?.let { visit(it) }
        return RhovasIr.Statement.Try(body, catches, finallyStatement)
    }

    override fun visit(ast: RhovasAst.Statement.Try.Catch): RhovasIr.Statement.Try.Catch {
        return scoped(Scope(scope)) {
            //TODO: Catch type
            scope.variables.define(Variable(ast.name, Library.TYPES["Exception"]!!))
            val body = visit(ast.body)
            RhovasIr.Statement.Try.Catch(ast.name, body)
        }
    }

    override fun visit(ast: RhovasAst.Statement.With): RhovasIr.Statement.With {
        val argument = visit(ast.argument)
        return scoped(Scope(scope)) {
            ast.name?.let { scope.variables.define(Variable(ast.name, argument.type)) }
            val body = visit(ast.body)
            RhovasIr.Statement.With(ast.name, argument, body)
        }
    }

    override fun visit(ast: RhovasAst.Statement.Label): RhovasIr.Statement.Label {
        require(ast.statement is RhovasAst.Statement.For || ast.statement is RhovasAst.Statement.While) { error(
            ast.statement,
            "Invalid label statement.",
            "A label statement requires the statement to be a for/while loop.",
        ) }
        //TODO: Validate label
        val statement = visit(ast.statement)
        return RhovasIr.Statement.Label(ast.label, statement)
    }

    override fun visit(ast: RhovasAst.Statement.Break): RhovasIr.Statement.Break {
        //TODO: Validate label
        return RhovasIr.Statement.Break(ast.label)
    }

    override fun visit(ast: RhovasAst.Statement.Continue): RhovasIr.Statement.Continue {
        //TODO: Validate label
        return RhovasIr.Statement.Continue(ast.label)
    }

    override fun visit(ast: RhovasAst.Statement.Return): RhovasIr.Statement.Return {
        //TODO: Validate return value type
        val value = ast.value?.let { visit(it) }
        return RhovasIr.Statement.Return(value)
    }

    override fun visit(ast: RhovasAst.Statement.Throw): RhovasIr.Statement.Throw {
        val exception = visit(ast.exception)
        require(exception.type.isSubtypeOf(Library.TYPES["Exception"]!!)) { error(
            ast.exception,
            "Invalid throw expression type.",
            "An throw statement requires the expression to be type Exception, but received ${exception.type.name}.",
        ) }
        return RhovasIr.Statement.Throw(exception)
    }

    override fun visit(ast: RhovasAst.Statement.Assert): RhovasIr.Statement.Assert {
        val condition = visit(ast.condition)
        require(condition.type.isSubtypeOf(Library.TYPES["Boolean"]!!)) { error(
            ast.condition,
            "Invalid assert condition type.",
            "An assert statement requires the condition to be type Boolean, but received ${condition.type.name}.",
        ) }
        val message = ast.message?.let { visit(it) }
        require(message == null || message.type.isSubtypeOf(Library.TYPES["String"]!!)) { error(
            ast.message,
            "Invalid assert message type.",
            "An assert statement requires the message to be type String, but received ${message!!.type.name}.",
        ) }
        return RhovasIr.Statement.Assert(condition, message)
    }

    override fun visit(ast: RhovasAst.Statement.Require): RhovasIr.Statement.Require {
        val condition = visit(ast.condition)
        require(condition.type.isSubtypeOf(Library.TYPES["Boolean"]!!)) { error(
            ast.condition,
            "Invalid require condition type.",
            "A require statement requires the condition to be type Boolean, but received ${condition.type.name}.",
        ) }
        val message = ast.message?.let { visit(it) }
        require(message == null || message.type.isSubtypeOf(Library.TYPES["String"]!!)) { error(
            ast.message,
            "Invalid require message type.",
            "A require statement requires the message to be type String, but received ${message!!.type.name}.",
        ) }
        return RhovasIr.Statement.Require(condition, message)
    }

    override fun visit(ast: RhovasAst.Statement.Ensure): RhovasIr.Statement.Ensure {
        val condition = visit(ast.condition)
        require(condition.type.isSubtypeOf(Library.TYPES["Boolean"]!!)) { error(
            ast.condition,
            "Invalid ensure condition type.",
            "An ensure statement requires the condition to be type Boolean, but received ${condition.type.name}.",
        ) }
        val message = ast.message?.let { visit(it) }
        require(message == null || message.type.isSubtypeOf(Library.TYPES["String"]!!)) { error(
            ast.message,
            "Invalid ensure message type.",
            "An ensure statement requires the message to be type String, but received ${message!!.type.name}.",
        ) }
        return RhovasIr.Statement.Ensure(condition, message)
    }

    private fun visit(ast: RhovasAst.Expression): RhovasIr.Expression {
        return super.visit(ast) as RhovasIr.Expression
    }

    override fun visit(ast: RhovasAst.Expression.Literal): RhovasIr.Expression.Literal {
        return when (ast.value) {
            is List<*> -> {
                val value = (ast.value as List<RhovasAst.Expression>).map { visit(it) }
                RhovasIr.Expression.Literal(value, Library.TYPES["List"]!!)
            }
            is Map<*, *> -> {
                val value = (ast.value as Map<String, RhovasAst.Expression>).mapValues { visit(it.value) }
                RhovasIr.Expression.Literal(value, Library.TYPES["Map"]!!)
            }
            else -> {
                val type = when (ast.value) {
                    null -> Library.TYPES["Null"]!!
                    is Boolean -> Library.TYPES["Boolean"]!!
                    is BigInteger -> Library.TYPES["Integer"]!!
                    is BigDecimal -> Library.TYPES["Decimal"]!!
                    is String -> Library.TYPES["String"]!!
                    is RhovasAst.Atom -> Library.TYPES["Atom"]!!
                    else -> throw AssertionError()
                }
                RhovasIr.Expression.Literal(ast.value, type)
            }
        }
    }

    override fun visit(ast: RhovasAst.Expression.Group): RhovasIr.Expression.Group {
        val expression = visit(ast.expression)
        return RhovasIr.Expression.Group(expression)
    }

    override fun visit(ast: RhovasAst.Expression.Unary): RhovasIr.Expression.Unary {
        val expression = visit(ast.expression)
        val method = expression.type.methods[ast.operator, 1]?.let { Method(it) } ?: throw error(
            ast,
            "Undefined method.",
            "The method ${expression.type.name}.${ast.operator}/0 is not defined.",
        )
        return RhovasIr.Expression.Unary(ast.operator, expression, method)
    }

    override fun visit(ast: RhovasAst.Expression.Binary): RhovasIr.Expression.Binary {
        val left = visit(ast.left)
        val method = left.type.methods[ast.operator, 2]?.let { Method(it) } ?: throw error(
            ast,
            "Undefined method.",
            "The method ${left.type.name}.${ast.operator}/1 is not defined.",
        )
        val right = visit(ast.right)
        require(right.type.isSubtypeOf(method.parameters[1].second)) { throw error(
            ast.right,
            "Invalid method argument type.",
            "The method ${left.type.name}.${ast.operator}/1 requires argument ${0} to be type ${method.parameters[1].second.name}, but received ${right.type.name}",
        ) }
        return RhovasIr.Expression.Binary(ast.operator, left, right, method)
    }

    override fun visit(ast: RhovasAst.Expression.Access.Variable): RhovasIr.Expression.Access.Variable {
        val variable = scope.variables[ast.name] ?: throw error(
            ast,
            "Undefined variable.",
            "The variable ${ast.name} is not defined in the current scope."
        )
        return RhovasIr.Expression.Access.Variable(variable)
    }

    override fun visit(ast: RhovasAst.Expression.Access.Property): RhovasIr.Expression.Access.Property {
        val receiver = visit(ast.receiver)
        val method = receiver.type.methods[ast.name, 1]?.let { Method(it) } ?: throw error(
            ast,
            "Undefined property.",
            "The property getter ${receiver.type.name}.${ast.name}/0 is not defined.",
        )
        //TODO: Coalesce typechecking (requires nullable types)
        return RhovasIr.Expression.Access.Property(receiver, method, ast.coalesce)
    }

    override fun visit(ast: RhovasAst.Expression.Access.Index): RhovasIr.Expression.Access.Index {
        val receiver = visit(ast.receiver)
        val method = receiver.type.methods["[]", 1 + ast.arguments.size]?.let { Method(it) } ?: throw error(
            ast,
            "Undefined method.",
            "The method ${receiver.type.name}.[]/${ast.arguments.size} is not defined.",
        )
        val arguments = ast.arguments.map { visit(it) }
        for (i in arguments.indices) {
            require(arguments[i].type.isSubtypeOf(method.parameters[i].second)) { throw error(
                ast.arguments[i],
                "Invalid method argument type.",
                "The method ${receiver.type.name}.[]=/${method.parameters.size} requires argument ${i} to be type ${method.parameters[i].second.name}, but received ${arguments[i].type.name}",
            ) }
        }
        return RhovasIr.Expression.Access.Index(receiver, method, arguments)
    }

    override fun visit(ast: RhovasAst.Expression.Invoke.Function): RhovasIr.Expression.Invoke.Function {
        val function = scope.functions[ast.name, ast.arguments.size] ?: throw error(
            ast,
            "Undefined function.",
            "The function ${ast.name}/${ast.arguments.size} is not defined in the current scope.",
        )
        val arguments = ast.arguments.map { visit(it) }
        for (i in arguments.indices) {
            require(arguments[i].type.isSubtypeOf(function.parameters[i].second)) { throw error(
                ast.arguments[i],
                "Invalid function argument type.",
                "The function ${function.name}/${function.parameters.size} requires argument ${i} to be type ${function.parameters[i].second.name}, but received ${arguments[i].type.name}",
            ) }
        }
        return RhovasIr.Expression.Invoke.Function(function, arguments)
    }

    override fun visit(ast: RhovasAst.Expression.Invoke.Method): RhovasIr.Expression.Invoke.Method {
        val receiver = visit(ast.receiver)
        val method = receiver.type.methods[ast.name, 1 + ast.arguments.size]?.let { Method(it) } ?: throw error(
            ast,
            "Undefined method.",
            "The method ${receiver.type.name}.${ast.name}/${ast.arguments.size} is not defined.",
        )
        val arguments = ast.arguments.map { visit(it) }
        for (i in arguments.indices) {
            require(arguments[i].type.isSubtypeOf(method.parameters[i].second)) { throw error(
                ast.arguments[i],
                "Invalid method argument type.",
                "The method ${receiver.type.name}.[]=/${method.parameters.size} requires argument ${i} to be type ${method.parameters[i].second.name}, but received ${arguments[i].type.name}",
            ) }
        }
        //TODO: Coalesce typechecking (requires nullable types)
        return RhovasIr.Expression.Invoke.Method(receiver, method, ast.coalesce, ast.cascade, arguments)
    }

    override fun visit(ast: RhovasAst.Expression.Invoke.Pipeline): RhovasIr.Expression.Invoke.Pipeline {
        val receiver = visit(ast.receiver)
        return if (ast.qualifier == null) {
            val function = scope.functions[ast.name, 1 + ast.arguments.size] ?: throw error(
                ast,
                "Undefined function.",
                "The function ${ast.name}/${1 + ast.arguments.size} is not defined in the current scope.",
            )
            val arguments = listOf(receiver) + ast.arguments.map { visit(it) }
            for (i in arguments.indices) {
                require(arguments[i].type.isSubtypeOf(function.parameters[i].second)) { throw error(
                    ast.arguments[i],
                    "Invalid function argument type.",
                    "The function ${function.name}/${function.parameters.size} requires argument ${i} to be type ${function.parameters[i].second.name}, but received ${arguments[i].type.name}",
                ) }
            }
            //TODO: Coalesce typechecking (requires nullable types)
            RhovasIr.Expression.Invoke.Pipeline(receiver, null, function, ast.coalesce, ast.cascade, arguments)
        } else {
            val qualifier = visit(ast.qualifier) as RhovasIr.Expression.Access
            //TODO: Static function scope
            val method = qualifier.type.methods[ast.name, 1 + 1 + ast.arguments.size]?.let { Method(it) } ?: throw error(
                ast,
                "Undefined method.",
                "The method ${qualifier.type.name}.${ast.name}/${1 + ast.arguments.size} is not defined.",
            )
            require(receiver.type.isSubtypeOf(method.parameters[0].second)) { error(
                ast,
                "Invalid method argument type.",
                "The method ${qualifier.type.name}.${ast.name}/${1 + ast.arguments.size} requires argument 0 to be type ${method.parameters[0].second.name}, but received ${receiver.type.name}",
            ) }
            val arguments = ast.arguments.map { visit(it) }
            for (i in arguments.indices) {
                require(arguments[i].type.isSubtypeOf(method.parameters[1 + i].second)) { throw error(
                    ast.arguments[i],
                    "Invalid method argument type.",
                    "The method ${qualifier.type.name}.${ast.name}/${1 + arguments.size} requires argument ${i} to be type ${method.parameters[1 + i].second.name}, but received ${arguments[i].type.name}",
                ) }
            }
            //TODO: Coalesce typechecking (requires nullable types)
            RhovasIr.Expression.Invoke.Pipeline(receiver, qualifier, method.function, ast.coalesce, ast.cascade, arguments)
        }
    }

    override fun visit(ast: RhovasAst.Expression.Lambda): RhovasIr.Expression.Lambda {
        //TODO: Validate using same requirements as function statements
        val parameters = ast.parameters.map { Pair(it.first, it.second?.let { visit(it) }) }
        val body = visit(ast.body)
        val type = Library.TYPES["Any"]!! //TODO: Requires type inference/unification
        return RhovasIr.Expression.Lambda(parameters, body, type)
    }

    override fun visit(ast: RhovasAst.Expression.Macro): RhovasIr.Expression.Macro {
        TODO()
    }

    override fun visit(ast: RhovasAst.Expression.Dsl): RhovasIr.Expression.Dsl {
        TODO()
    }

    override fun visit(ast: RhovasAst.Expression.Interpolation): RhovasIr.Expression.Interpolation {
        val expression = visit(ast.expression)
        return RhovasIr.Expression.Interpolation(expression)
    }

    private fun visit(ast: RhovasAst.Pattern): RhovasIr.Pattern {
        return super.visit(ast) as RhovasIr.Pattern
    }

    override fun visit(ast: RhovasAst.Pattern.Variable): RhovasIr.Pattern.Variable {
        //TODO: Validate variable name
        //TODO: Infer type
        val variable = Variable(ast.name, Library.TYPES["Any"]!!)
        return RhovasIr.Pattern.Variable(variable)
    }

    override fun visit(ast: RhovasAst.Pattern.Value): RhovasIr.Pattern.Value {
        //TODO: Value typechecking
        val value = visit(ast.value)
        return RhovasIr.Pattern.Value(value)
    }

    override fun visit(ast: RhovasAst.Pattern.Predicate): RhovasIr.Pattern.Predicate {
        val pattern = visit(ast.pattern)
        //TODO: Bind variables
        val predicate = scoped(Scope(scope)) {
            scope.variables.define(Variable("val", pattern.type))
            visit(ast.predicate)
        }
        require(predicate.type.isSubtypeOf(Library.TYPES["Boolean"]!!)) { error(
            ast.predicate,
            "Invalid pattern predicate type.",
            "A predicate pattern requires the predicate to be type Boolean, but received ${predicate.type}.",
        ) }
        return RhovasIr.Pattern.Predicate(pattern, predicate)
    }

    override fun visit(ast: RhovasAst.Pattern.OrderedDestructure): RhovasIr.Pattern.OrderedDestructure {
        //TODO: Value typechecking
        var vararg = false
        val patterns = ast.patterns.withIndex().map {
            if (it.value is RhovasAst.Pattern.VarargDestructure) {
                require(!vararg) { error(
                    it.value,
                    "Invalid multiple varargs.",
                    "An ordered destructure requires no more than one vararg pattern.",
                ) }
                vararg = true
                val ast = it.value as RhovasAst.Pattern.VarargDestructure
                val pattern = ast.pattern?.let { visit(it) }
                RhovasIr.Pattern.VarargDestructure(pattern, ast.operator, Library.TYPES["List"]!!)
            } else {
                visit(it.value)
            }
        }
        return RhovasIr.Pattern.OrderedDestructure(patterns, Library.TYPES["List"]!!)
    }

    override fun visit(ast: RhovasAst.Pattern.NamedDestructure): RhovasIr.Pattern.NamedDestructure {
        //TODO: Value typechecking
        //TODO: Validate keys/variable names
        var vararg = false
        val patterns = ast.patterns.withIndex().map {
            val pattern = if (it.value.second is RhovasAst.Pattern.VarargDestructure) {
                //TODO: Consider requiring varargs as the last pattern
                require(!vararg) { error(
                    it.value.second,
                    "Invalid multiple varargs.",
                    "A named destructure requires no more than one vararg pattern.",
                ) }
                vararg = true
                val ast = it.value.second as RhovasAst.Pattern.VarargDestructure
                val pattern = ast.pattern?.let { visit(it) }
                RhovasIr.Pattern.VarargDestructure(pattern, ast.operator, Library.TYPES["Map"]!!)
            } else {
                it.value.second?.let { visit(it) }
            }
            Pair(it.value.first, pattern)
        }
        return RhovasIr.Pattern.NamedDestructure(patterns, Library.TYPES["Map"]!!)
    }

    override fun visit(ast: RhovasAst.Pattern.TypedDestructure): RhovasIr.Pattern.TypedDestructure {
        val type = visit(ast.type)
        //TODO: Value typechecking
        val pattern = ast.pattern?.let { visit(it) }
        return RhovasIr.Pattern.TypedDestructure(type.type, pattern)
    }

    override fun visit(ast: RhovasAst.Pattern.VarargDestructure): RhovasIr.Pattern.VarargDestructure {
        throw AssertionError()
    }

    override fun visit(ast: RhovasAst.Type): RhovasIr.Type {
        val type = Library.TYPES[ast.name] ?: throw error(
            ast,
            "Undefined type.",
            "The type ${ast.name} is not defined."
        )
        return RhovasIr.Type(type)
    }

}
