package dev.rhovas.interpreter.analyzer.rhovas

import dev.rhovas.interpreter.analyzer.Analyzer
import dev.rhovas.interpreter.environment.Function
import dev.rhovas.interpreter.environment.Scope
import dev.rhovas.interpreter.environment.Type
import dev.rhovas.interpreter.environment.Variable
import dev.rhovas.interpreter.library.Library
import dev.rhovas.interpreter.parser.dsl.DslAst
import dev.rhovas.interpreter.parser.rhovas.RhovasAst
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
            val statements = ast.statements.withIndex().map {
                require(it.index == ast.statements.lastIndex || context.jumps.isEmpty()) { error(
                    ast,
                    "Unreachable statement.",
                    "The previous statement changes control flow to always jump past this statement.",
                ) }
                visit(it.value)
            }
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
        val parameters = ast.parameters.map { Pair(it.first, it.second?.let { visit(it).type } ?: Library.TYPES["Dynamic"]!!) }
        val returns = ast.returns?.let { visit(it).type } ?: Library.TYPES["Void"]!! //TODO or Dynamic?
        val function = Function.Definition(ast.name, parameters, returns)
        scope.functions.define(function)
        //TODO: Validate thrown exceptions
        return scoped(Scope(scope)) {
            val previous = context
            context = Context(function)
            parameters.forEach { scope.variables.define(Variable.Local(it.first, it.second, false)) }
            val body = visit(ast.body) as RhovasIr.Statement.Block
            require(function.returns.isSubtypeOf(Library.TYPES["Void"]!!) || context.jumps.contains("")) { error(
                ast,
                "Missing return value.",
                "The function ${ast.name}/${ast.parameters.size} requires a return value.",
            ) }
            context = previous
            RhovasIr.Statement.Function(function, body)
        }
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
        val variable = Variable.Local(ast.name, type?.type ?: value!!.type, ast.mutable)
        require(value == null || value.type.isSubtypeOf(variable.type)) { error(
            ast,
            "Invalid value type.",
            "The variable ${ast.name} requires a value of type ${variable.type}, but received ${value!!.type}."
        ) }
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
                val receiver = visit(ast.receiver)
                require(receiver.variable.mutable) { error(
                    ast.receiver,
                    "Unassignable variable.",
                    "The variable ${receiver.variable.name} is not assignable.",
                ) }
                val value = visit(ast.value)
                require(value.type.isSubtypeOf(receiver.variable.type)) { error(
                    ast.value,
                    "Invalid assignment value type.",
                    "The variable ${receiver.variable.name} requires the value to be type ${receiver.variable.type}, but received ${value.type}.",
                ) }
                RhovasIr.Statement.Assignment.Variable(receiver.variable, value)
            }
            is RhovasAst.Expression.Access.Property -> {
                require(!ast.receiver.coalesce) { error(
                    ast.receiver,
                    "Invalid assignment receiver.",
                    "An assignment statement requires the receiver property access to be non-coalescing.",
                ) }
                val receiver = visit(ast.receiver)
                require(receiver.property.mutable) { error(
                    ast.receiver,
                    "Unassignable property.",
                    "The property ${receiver.property.type.base.name}.${receiver.property.name} is not assignable.",
                ) }
                val value = visit(ast.value)
                require(value.type.isSubtypeOf(receiver.property.type)) { error(
                    ast.value,
                    "Invalid assignment value type.",
                    "The property ${receiver.property.type.base.name}.${receiver.property.name} requires the value to be type ${receiver.property.type}, but received ${value.type}.",
                ) }
                RhovasIr.Statement.Assignment.Property(receiver.receiver, receiver.property, value)
            }
            is RhovasAst.Expression.Access.Index -> {
                val receiver = visit(ast.receiver.receiver)
                val method = receiver.type.methods["[]=", ast.receiver.arguments.size + 1] ?: throw error(
                    ast,
                    "Undefined method.",
                    "The method ${receiver.type.base.name}.[]=/${ast.receiver.arguments.size + 1} is not defined.",
                )
                val arguments = ast.receiver.arguments.map { visit(it) }
                for (i in arguments.indices) {
                    require(arguments[i].type.isSubtypeOf(method.parameters[i].second)) { error(
                        ast.receiver.arguments[i],
                        "Invalid method argument type.",
                        "The method ${receiver.type.base.name}.[]=/${ast.receiver.arguments.size + 1} requires argument ${i} to be type ${method.parameters[i].second}, but received ${arguments[i].type}",
                    ) }
                }
                val value = visit(ast.value)
                require(value.type.isSubtypeOf(method.parameters.last().second)) { error(
                    ast.value,
                    "Invalid assignment value type.",
                    "The method ${receiver.type.base.name}.[]=/${ast.receiver.arguments.size + 1} requires the value to be type ${method.parameters.last().second}, but received ${value.type}.",
                ) }
                RhovasIr.Statement.Assignment.Index(receiver, method, arguments, value)
            }
            else -> throw AssertionError()
        }
    }

    override fun visit(ast: RhovasAst.Statement.If): RhovasIr.Statement.If {
        val condition = visit(ast.condition)
        require(condition.type.isSubtypeOf(Library.TYPES["Boolean"]!!)) { error(
            ast.condition,
            "Invalid if condition type.",
            "An if statement requires the condition to be type Boolean, but received ${condition.type}.",
        ) }
        val previous = context
        context = previous.child()
        val thenStatement = visit(ast.thenStatement)
        context = previous.child()
        val elseStatement = ast.elseStatement?.let { visit(it) }
        context = previous.collect()
        return RhovasIr.Statement.If(condition, thenStatement, elseStatement)
    }

    override fun visit(ast: RhovasAst.Statement.Match.Conditional): RhovasIr {
        fun visitCondition(ast: RhovasAst.Expression): RhovasIr.Expression {
            val condition = visit(ast)
            require(condition.type.isSubtypeOf(Library.TYPES["Boolean"]!!)) { error(
                ast,
                "Invalid match condition type.",
                "A conditional match statement requires the condition to be type Boolean, but received ${condition.type}.",
            ) }
            return condition
        }
        val previous = context
        val cases = ast.cases.map {
            context = previous.child()
            val condition = visitCondition(it.first)
            val statement = visit(it.second)
            Pair(condition, statement)
        }
        context = previous.child()
        val elseCase = ast.elseCase?.let {
            val condition = it.first?.let { visitCondition(it) }
            val statement = visit(it.second)
            Pair(condition, statement)
        }
        context = previous.collect()
        return RhovasIr.Statement.Match.Conditional(cases, elseCase)
    }

    override fun visit(ast: RhovasAst.Statement.Match.Structural): RhovasIr {
        val argument = visit(ast.argument)
        //TODO: Typecheck patterns
        val previous = context
        val cases = ast.cases.map {
            scoped(Scope(scope)) {
                context = previous.child()
                val pattern = visit(it.first)
                val statement = visit(it.second)
                Pair(pattern, statement)
            }
        }
        val elseCase = ast.elseCase?.let {
            scoped(Scope(scope)) {
                context = previous.child()
                val pattern = it.first?.let { visit(it) }
                val statement = visit(it.second)
                Pair(pattern, statement)
            }
        }
        context = context.collect()
        return RhovasIr.Statement.Match.Structural(argument, cases, elseCase)
    }

    override fun visit(ast: RhovasAst.Statement.For): RhovasIr.Statement.For {
        val argument = visit(ast.argument)
        //TODO: Iterable type
        require(argument.type.isSubtypeOf(Library.TYPES["List"]!!)) { error(
            ast.argument,
            "Invalid for loop argument type.",
            "A for loop requires the argument to be type List, but received ${argument.type}.",
        ) }
        //TODO: Proper generic type access
        val type = argument.type.methods["get", 1]!!.returns
        return scoped(Scope(scope)) {
            //TODO: Generic types
            scope.variables.define(Variable.Local(ast.name, type, false))
            val previous = context.labels.putIfAbsent(null, false)
            val body = visit(ast.body)
            if (previous != null) {
                context.labels[null] = previous
            } else {
                context.labels.remove(null)
            }
            //TODO: Validate jump context
            context.jumps.clear()
            RhovasIr.Statement.For(ast.name, argument, body)
        }
    }

    override fun visit(ast: RhovasAst.Statement.While): RhovasIr.Statement.While {
        val condition = visit(ast.condition)
        require(condition.type.isSubtypeOf(Library.TYPES["Boolean"]!!)) { error(
            ast.condition,
            "Invalid while condition type.",
            "An while statement requires the condition to be type Boolean, but received ${condition.type}.",
        ) }
        val previous = context.labels.putIfAbsent(null, false)
        val body = visit(ast.body)
        if (previous != null) {
            context.labels[null] = previous
        } else {
            context.labels.remove(null)
        }
        //TODO: Validate jump context
        context.jumps.clear()
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
            scope.variables.define(Variable.Local(ast.name, Library.TYPES["Exception"]!!, false))
            val body = visit(ast.body)
            RhovasIr.Statement.Try.Catch(ast.name, body)
        }
    }

    override fun visit(ast: RhovasAst.Statement.With): RhovasIr.Statement.With {
        val argument = visit(ast.argument)
        return scoped(Scope(scope)) {
            ast.name?.let { scope.variables.define(Variable.Local(ast.name, argument.type, false)) }
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
        require(!context.labels.contains(ast.label)) { error(
            ast,
            "Redefined label.",
            "The label ${ast.label} is already defined in this scope.",
        ) }
        context.labels[ast.label] = false
        val statement = visit(ast.statement)
        //TODO: Validate unused labels
        require(context.labels[ast.label] == true) { error(
            ast,
            "Unused label.",
            "The label ${ast.label} is unused within the statement.",
        ) }
        context.labels.remove(ast.label)
        //TODO: Validate jump locations (constant conditions / dependent types)
        return RhovasIr.Statement.Label(ast.label, statement)
    }

    override fun visit(ast: RhovasAst.Statement.Break): RhovasIr.Statement.Break {
        require(context.labels.contains(null)) { error(
            ast,
            "Invalid continue statement.",
            "A continue statement requires an enclosing for/while loop.",
        ) }
        require(context.labels.contains(ast.label)) { error(
            ast,
            "Undefined label.",
            "The label ${ast.label} is not defined in this scope.",
        ) }
        context.labels[ast.label] = true
        context.jumps.add(ast.label)
        return RhovasIr.Statement.Break(ast.label)
    }

    override fun visit(ast: RhovasAst.Statement.Continue): RhovasIr.Statement.Continue {
        require(context.labels.contains(null)) { error(
            ast,
            "Invalid continue statement.",
            "A continue statement requires an enclosing for/while loop.",
        ) }
        require(context.labels.contains(ast.label)) { error(
            ast,
            "Undefined label.",
            "The label ${ast.label} is not defined in this scope.",
        ) }
        context.labels[ast.label] = true
        return RhovasIr.Statement.Continue(ast.label)
    }

    override fun visit(ast: RhovasAst.Statement.Return): RhovasIr.Statement.Return {
        require(context.function != null) { error(
            ast,
            "Invalid return statement.",
            "A return statement requires an enclosing function definition.",
        ) }
        val value = ast.value?.let { visit(it) }
        require((value?.type ?: Library.TYPES["Void"]!!).isSubtypeOf(context.function!!.returns)) { error(
            ast,
            "Invalid return value type.",
            "The enclosing function ${context.function!!.name}/${context.function!!.parameters.size} requires the return value to be type ${context.function!!.returns}, but received ${value?.type ?: Library.TYPES["Void"]!!}.",
        ) }
        context.jumps.add("")
        println("Return Post Context: " + context.jumps.size)
        return RhovasIr.Statement.Return(value)
    }

    override fun visit(ast: RhovasAst.Statement.Throw): RhovasIr.Statement.Throw {
        val exception = visit(ast.exception)
        require(exception.type.isSubtypeOf(Library.TYPES["Exception"]!!)) { error(
            ast.exception,
            "Invalid throw expression type.",
            "An throw statement requires the expression to be type Exception, but received ${exception.type}.",
        ) }
        return RhovasIr.Statement.Throw(exception)
    }

    override fun visit(ast: RhovasAst.Statement.Assert): RhovasIr.Statement.Assert {
        val condition = visit(ast.condition)
        require(condition.type.isSubtypeOf(Library.TYPES["Boolean"]!!)) { error(
            ast.condition,
            "Invalid assert condition type.",
            "An assert statement requires the condition to be type Boolean, but received ${condition.type}.",
        ) }
        val message = ast.message?.let { visit(it) }
        require(message == null || message.type.isSubtypeOf(Library.TYPES["String"]!!)) { error(
            ast.message,
            "Invalid assert message type.",
            "An assert statement requires the message to be type String, but received ${message!!.type}.",
        ) }
        return RhovasIr.Statement.Assert(condition, message)
    }

    override fun visit(ast: RhovasAst.Statement.Require): RhovasIr.Statement.Require {
        val condition = visit(ast.condition)
        require(condition.type.isSubtypeOf(Library.TYPES["Boolean"]!!)) { error(
            ast.condition,
            "Invalid require condition type.",
            "A require statement requires the condition to be type Boolean, but received ${condition.type}.",
        ) }
        val message = ast.message?.let { visit(it) }
        require(message == null || message.type.isSubtypeOf(Library.TYPES["String"]!!)) { error(
            ast.message,
            "Invalid require message type.",
            "A require statement requires the message to be type String, but received ${message!!.type}.",
        ) }
        return RhovasIr.Statement.Require(condition, message)
    }

    override fun visit(ast: RhovasAst.Statement.Ensure): RhovasIr.Statement.Ensure {
        val condition = visit(ast.condition)
        require(condition.type.isSubtypeOf(Library.TYPES["Boolean"]!!)) { error(
            ast.condition,
            "Invalid ensure condition type.",
            "An ensure statement requires the condition to be type Boolean, but received ${condition.type}.",
        ) }
        val message = ast.message?.let { visit(it) }
        require(message == null || message.type.isSubtypeOf(Library.TYPES["String"]!!)) { error(
            ast.message,
            "Invalid ensure message type.",
            "An ensure statement requires the message to be type String, but received ${message!!.type}.",
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
                RhovasIr.Expression.Literal(value, Type.Reference(Library.TYPES["List"]!!.base, listOf(Library.TYPES["Dynamic"]!!)))
            }
            is Map<*, *> -> {
                val value = (ast.value as Map<String, RhovasAst.Expression>).mapValues { visit(it.value) }
                RhovasIr.Expression.Literal(value, Library.TYPES["Object"]!!)
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
        val method = expression.type.methods[ast.operator, 0] ?: throw error(
            ast,
            "Undefined method.",
            "The method ${expression.type.base.name}.${ast.operator}/0 is not defined.",
        )
        return RhovasIr.Expression.Unary(ast.operator, expression, method)
    }

    override fun visit(ast: RhovasAst.Expression.Binary): RhovasIr.Expression.Binary {
        val left = visit(ast.left)
        val right = visit(ast.right)
        val type = when (ast.operator) {
            "&&", "||" -> {
                require(left.type.isSubtypeOf(Library.TYPES["Boolean"]!!)) { error(
                    ast.left,
                    "Invalid binary operand.",
                    "A logical binary expression requires the left operand to be type Boolean, but received ${left.type}.",
                ) }
                require(right.type.isSubtypeOf(Library.TYPES["Boolean"]!!)) { error(
                    ast.right,
                    "Invalid binary operand.",
                    "A logical binary expression requires the left operand to be type Boolean, but received ${left.type}.",
                ) }
                Library.TYPES["Boolean"]!!
            }
            "==", "!=" -> {
                require(left.type.methods["==", 1] != null) { error(
                    ast,
                    "Undefined method.",
                    "The method ${left.type.base.name}.==/1 is not defined.",
                ) }
                Library.TYPES["Boolean"]!!
            }
            "===", "!==" -> {
                Library.TYPES["Boolean"]!!
            }
            "<", ">", "<=", ">=" -> {
                val method = left.type.methods["<=>", 1] ?: throw error(
                    ast,
                    "Undefined method.",
                    "The method ${left.type.base.name}.<=>/1 is not defined.",
                )
                require(right.type.isSubtypeOf(method.parameters[0].second)) { error(
                    ast.right,
                    "Invalid method argument type.",
                    "The method ${left.type.base.name}.<=>/1 requires argument ${0} to be type ${method.parameters[0].second}, but received ${right.type}",
                ) }
                Library.TYPES["Boolean"]!!
            }
            "+", "-", "*", "/" -> {
                val method = left.type.methods[ast.operator, 1] ?: throw error(
                    ast,
                    "Undefined method.",
                    "The method ${left.type.base.name}.${ast.operator}/1 is not defined.",
                )
                require(right.type.isSubtypeOf(method.parameters[0].second)) { error(
                    ast.right,
                    "Invalid method argument type.",
                    "The method ${left.type.base.name}.${ast.operator}/1 requires argument ${0} to be type ${method.parameters[0].second}, but received ${right.type}",
                ) }
                method.returns
            }
            else -> throw AssertionError()
        }
        return RhovasIr.Expression.Binary(ast.operator, left, right, type)
    }

    override fun visit(ast: RhovasAst.Expression.Access.Variable): RhovasIr.Expression.Access.Variable {
        val variable = scope.variables[ast.name] ?: throw error(
            ast,
            "Undefined variable.",
            "The variable ${ast.name} is not defined in the current scope."
        )
        //TODO: Variable.Local handling
        return RhovasIr.Expression.Access.Variable(when (variable) {
            is Variable.Local -> variable
            is Variable.Local.Runtime -> variable.variable
            else -> throw AssertionError()
        })
    }

    override fun visit(ast: RhovasAst.Expression.Access.Property): RhovasIr.Expression.Access.Property {
        val receiver = visit(ast.receiver)
        val method = receiver.type.properties[ast.name] ?: throw error(
            ast,
            "Undefined property.",
            "The property getter ${receiver.type.base.name}.${ast.name}/0 is not defined.",
        )
        //TODO: Coalesce typechecking (requires nullable types)
        return RhovasIr.Expression.Access.Property(receiver, method, ast.coalesce)
    }

    override fun visit(ast: RhovasAst.Expression.Access.Index): RhovasIr.Expression.Access.Index {
        val receiver = visit(ast.receiver)
        val method = receiver.type.methods["[]", ast.arguments.size] ?: throw error(
            ast,
            "Undefined method.",
            "The method ${receiver.type.base.name}.[]/${ast.arguments.size} is not defined.",
        )
        val arguments = ast.arguments.map { visit(it) }
        for (i in arguments.indices) {
            require(arguments[i].type.isSubtypeOf(method.parameters[i].second)) { error(
                ast.arguments[i],
                "Invalid method argument type.",
                "The method ${receiver.type.base.name}.[]=/${method.parameters.size} requires argument ${i} to be type ${method.parameters[i].second}, but received ${arguments[i].type}",
            ) }
        }
        return RhovasIr.Expression.Access.Index(receiver, method, arguments)
    }

    override fun visit(ast: RhovasAst.Expression.Invoke.Function): RhovasIr.Expression.Invoke.Function {
        val function = scope.functions[ast.name, ast.arguments.size] as Function.Definition? ?: throw error(
            ast,
            "Undefined function.",
            "The function ${ast.name}/${ast.arguments.size} is not defined in the current scope.",
        )
        val arguments = ast.arguments.map { visit(it) }
        for (i in arguments.indices) {
            require(arguments[i].type.isSubtypeOf(function.parameters[i].second)) { error(
                ast.arguments[i],
                "Invalid function argument type.",
                "The function ${function.name}/${function.parameters.size} requires argument ${i} to be type ${function.parameters[i].second}, but received ${arguments[i].type}",
            ) }
        }
        return RhovasIr.Expression.Invoke.Function(function, arguments)
    }

    override fun visit(ast: RhovasAst.Expression.Invoke.Method): RhovasIr.Expression.Invoke.Method {
        val receiver = visit(ast.receiver)
        val method = receiver.type.methods[ast.name, ast.arguments.size] ?: throw error(
            ast,
            "Undefined method.",
            "The method ${receiver.type.base.name}.${ast.name}/${ast.arguments.size} is not defined.",
        )
        val arguments = ast.arguments.map { visit(it) }
        for (i in arguments.indices) {
            require(arguments[i].type.isSubtypeOf(method.parameters[i].second)) { error(
                ast.arguments[i],
                "Invalid method argument type.",
                "The method ${receiver.type.base.name}.${method.name}/${method.parameters.size} requires argument ${i} to be type ${method.parameters[i].second}, but received ${arguments[i].type}",
            ) }
        }
        //TODO: Coalesce typechecking (requires nullable types)
        return RhovasIr.Expression.Invoke.Method(receiver, method, ast.coalesce, ast.cascade, arguments)
    }

    override fun visit(ast: RhovasAst.Expression.Invoke.Pipeline): RhovasIr.Expression.Invoke.Pipeline {
        val receiver = visit(ast.receiver)
        val function = if (ast.qualifier == null) {
            scope.functions[ast.name, 1 + ast.arguments.size] as Function.Definition? ?: throw error(
                ast,
                "Undefined function.",
                "The function ${ast.name}/${1 + ast.arguments.size} is not defined in the current scope.",
            )
        } else {
            val qualifier = visit(ast.qualifier) as RhovasIr.Expression.Access
            qualifier.type.functions[ast.name, 1 + ast.arguments.size] ?: throw error(
                ast,
                "Undefined function.",
                "The function ${qualifier.type.base.name}.${ast.name}/${1 + ast.arguments.size} is not defined.",
            )
        }
        require(receiver.type.isSubtypeOf(function.parameters[0].second)) { error(
            ast.receiver,
            "Invalid function argument type.",
            "The function ${function.name}/${1 + function.parameters.size} requires argument ${0} to be type ${function.parameters[0].second}, but received ${receiver.type}",
        ) }
        val arguments = ast.arguments.map { visit(it) }
        for (i in arguments.indices) {
            require(arguments[i].type.isSubtypeOf(function.parameters[1 + i].second)) { error(
                ast.arguments[i],
                "Invalid function argument type.",
                "The function ${function.name}/${1 + function.parameters.size} requires argument ${1 + i} to be type ${function.parameters[1 + i].second}, but received ${arguments[i].type}",
            ) }
        }
        //TODO: Coalesce typechecking (requires nullable types)
        return RhovasIr.Expression.Invoke.Pipeline(receiver, function, ast.coalesce, ast.cascade, arguments)
    }

    override fun visit(ast: RhovasAst.Expression.Lambda): RhovasIr.Expression.Lambda {
        //TODO: Validate using same requirements as function statements
        //TODO: Type inference/unification for parameter types
        val parameters = ast.parameters.map { Pair(it.first, it.second?.let { visit(it) }) }
        return scoped(Scope(scope)) {
            val previous = context
            context = Context(Function.Definition("lambda", parameters.map { Pair(it.first, it.second?.type ?: Library.TYPES["Dynamic"]!!) }, Library.TYPES["Dynamic"]!!))
            if (parameters.isNotEmpty()) {
                parameters.forEach { scope.variables.define(Variable.Local(it.first, Library.TYPES["Dynamic"]!!, false)) }
            } else {
                scope.variables.define(Variable.Local("val", Library.TYPES["Dynamic"]!!, false))
            }
            val body = visit(ast.body)
            context = previous
            val type = Type.Reference(Library.TYPES["Lambda"]!!.base, listOf(Library.TYPES["Dynamic"]!!, Library.TYPES["Dynamic"]!!))
            RhovasIr.Expression.Lambda(parameters, body, type)
        }
    }

    override fun visit(ast: RhovasAst.Expression.Macro): RhovasIr.Expression {
        return if (ast.arguments.last() is RhovasAst.Expression.Dsl) {
            //TODO: Syntax macro arguments
            visit(ast.arguments.last())
        } else {
            TODO()
        }
    }

    override fun visit(ast: RhovasAst.Expression.Dsl): RhovasIr.Expression {
        //TODO: Delegation of AST analysis
        val source = ast.ast as? DslAst.Source ?: throw error(
            ast,
            "Invalid DSL AST.",
            "The AST of type " + ast.ast + " is not supported by the analyzer.",
        )
        val function = scope.functions[ast.name, 2] as Function.Definition? ?: throw error(
            ast,
            "Undefined DSL transformer.",
            "The DSL ${ast.name} requires a transformer macro #${ast.name}/2 or function ${ast.name}/2.",
        )
        val literals = RhovasIr.Expression.Literal(
            source.literals.map { RhovasIr.Expression.Literal(it, Library.TYPES["String"]!!) },
            Type.Reference(Library.TYPES["List"]!!.base, listOf(Library.TYPES["String"]!!)),
        )
        val arguments = RhovasIr.Expression.Literal(
            source.arguments.map { visit(it as RhovasAst) },
            Type.Reference(Library.TYPES["List"]!!.base, listOf(Library.TYPES["Dynamic"]!!)),
        )
        //TODO: Compile time invocation
        return RhovasIr.Expression.Invoke.Function(function, listOf(literals, arguments))
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
        val variable = if (ast.name != "_") Variable.Local(ast.name, Library.TYPES["Dynamic"]!!, false) else null
        variable?.let { scope.variables.define(it) }
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
            scope.variables.define(Variable.Local("val", pattern.type, false))
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
                RhovasIr.Pattern.VarargDestructure(pattern, ast.operator, Type.Reference(Library.TYPES["List"]!!.base, listOf(Library.TYPES["Dynamic"]!!)))
            } else {
                visit(it.value)
            }
        }
        return RhovasIr.Pattern.OrderedDestructure(patterns, Type.Reference(Library.TYPES["List"]!!.base, listOf(Library.TYPES["Dynamic"]!!)))
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
                RhovasIr.Pattern.VarargDestructure(pattern, ast.operator, Library.TYPES["Object"]!!)
            } else if (it.value.second != null) {
                visit(it.value.second!!)
            } else {
                scope.variables.define(Variable.Local(it.value.first, Library.TYPES["Dynamic"]!!, false))
                null
            }
            Pair(it.value.first, pattern)
        }
        return RhovasIr.Pattern.NamedDestructure(patterns, Library.TYPES["Object"]!!)
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
        val base = Library.TYPES[ast.name]?.base ?: throw error(
            ast,
            "Undefined type.",
            "The type ${ast.name} is not defined."
        )
        val generics = ast.generics?.map { visit(ast).type } ?: listOf()
        require(base.generics.size == generics.size) { error(
            ast,
            "Invalid generic parameters.",
            "The type ${base.name} requires ${base.generics.size} generic parameters, but received ${generics.size}.",
        ) }
        for (i in generics.indices) {
            require(generics[i].isSubtypeOf(base.generics[i].bound)) { error(
                ast.generics!![i],
                "Invalid generic parameter.",
                "The type ${base.name} requires generic parameter ${i} to be type ${base.generics[i].bound}, but received ${generics[i]}.",
            ) }
        }
        val type = Type.Reference(base, generics)
        return RhovasIr.Type(type)
    }

}
