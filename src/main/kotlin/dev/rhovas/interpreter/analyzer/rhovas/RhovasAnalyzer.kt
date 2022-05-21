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

class RhovasAnalyzer(scope: Scope) :
    Analyzer(Context(null, listOf(
        InputContext(ArrayDeque()),
        ScopeContext(scope),
        LabelContext(mutableMapOf()),
        JumpContext(mutableSetOf())
    ).associateBy { it.javaClass })),
    RhovasAst.Visitor<RhovasIr> {

    private val Context.scope get() = this[ScopeContext::class.java]
    private val Context.labels get() = this[LabelContext::class.java]
    private val Context.jumps get() = this[JumpContext::class.java]

    data class ScopeContext(
        val scope: Scope,
    ) : Context.Item<Scope>(scope) {

        override fun child(): ScopeContext {
            return ScopeContext(Scope(scope))
        }

        override fun merge(children: List<Scope>) {}

    }

    data class LabelContext(
        val labels: MutableMap<String?, Boolean>,
    ) : Context.Item<MutableMap<String?, Boolean>>(labels) {

        override fun child(): LabelContext {
            return this
        }

        override fun merge(children: List<MutableMap<String?, Boolean>>) {}

    }

    data class JumpContext(
        val jumps: MutableSet<String?>,
    ) : Context.Item<MutableSet<String?>>(jumps) {

        override fun child(): Context.Item<MutableSet<String?>> {
            return JumpContext(mutableSetOf())
        }

        override fun merge(children: List<MutableSet<String?>>) {
            if (children.any { it.isEmpty() }) {
                jumps.clear()
            } else {
                children.forEach { jumps.addAll(it) }
            }
        }

    }

    override fun visit(ast: RhovasAst.Source): RhovasIr.Source {
        val statements = ast.statements.map { visit(it) }
        return RhovasIr.Source(statements).also {
            it.context = ast.context
        }
    }

    private fun visit(ast: RhovasAst.Statement): RhovasIr.Statement {
        return super.visit(ast) as RhovasIr.Statement
    }

    override fun visit(ast: RhovasAst.Statement.Block): RhovasIr {
        ast.context.firstOrNull()?.let { context.inputs.addLast(it) }
        return analyze {
            val statements = ast.statements.withIndex().map {
                require(it.index == ast.statements.lastIndex || context.jumps.isEmpty()) { error(
                    ast,
                    "Unreachable statement.",
                    "The previous statement changes control flow to always jump past this statement.",
                ) }
                visit(it.value)
            }
            RhovasIr.Statement.Block(statements).also {
                it.context = ast.context
                it.context.firstOrNull()?.let { context.inputs.removeLast() }
            }
        }
    }

    override fun visit(ast: RhovasAst.Statement.Expression): RhovasIr {
        ast.context.firstOrNull()?.let { context.inputs.addLast(it) }
        require(ast.expression is RhovasAst.Expression.Invoke) { error(
            ast.expression,
            "Invalid expression statement.",
            "An expression statement requires an invoke expression in order to perform a useful side-effect, but received ${ast.expression.javaClass.name}.",
        ) }
        val expression = visit(ast.expression) as RhovasIr.Expression.Invoke
        return RhovasIr.Statement.Expression(expression).also {
            it.context = ast.context
            it.context.firstOrNull()?.let { context.inputs.removeLast() }
        }
    }

    override fun visit(ast: RhovasAst.Statement.Function): RhovasIr.Statement.Function {
        ast.context.firstOrNull()?.let { context.inputs.addLast(it) }
        require(!context.scope.functions.isDefined(ast.name, ast.parameters.size, true)) { error(
            ast,
            "Redefined function.",
            "The function ${ast.name}/${ast.parameters.size} is already defined in this scope.",
        ) }
        val parameters = ast.parameters.map { Pair(it.first, it.second?.let { visit(it).type } ?: Library.TYPES["Dynamic"]!!) }
        val returns = ast.returns?.let { visit(it).type } ?: Library.TYPES["Void"]!! //TODO or Dynamic?
        val function = Function.Definition(ast.name, parameters, returns)
        context.scope.functions.define(function)
        //TODO: Validate thrown exceptions
        return analyze(context.child().copy(function = function)) {
            parameters.forEach { context.scope.variables.define(Variable.Local(it.first, it.second, false)) }
            val body = visit(ast.body) as RhovasIr.Statement.Block
            require(function.returns.isSubtypeOf(Library.TYPES["Void"]!!) || context.jumps.contains("")) { error(
                ast,
                "Missing return value.",
                "The function ${ast.name}/${ast.parameters.size} requires a return value.",
            ) }
            RhovasIr.Statement.Function(function, body).also {
                it.context = ast.context
                it.context.firstOrNull()?.let { context.inputs.removeLast() }
            }
        }
    }

    override fun visit(ast: RhovasAst.Statement.Declaration): RhovasIr.Statement.Declaration {
        ast.context.firstOrNull()?.let { context.inputs.addLast(it) }
        require(!context.scope.variables.isDefined(ast.name, true)) { error(
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
        context.scope.variables.define(variable)
        return RhovasIr.Statement.Declaration(variable, value).also {
            it.context = ast.context
            it.context.firstOrNull()?.let { context.inputs.removeLast() }
        }
    }

    override fun visit(ast: RhovasAst.Statement.Assignment): RhovasIr.Statement.Assignment {
        ast.context.firstOrNull()?.let { context.inputs.addLast(it) }
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
                RhovasIr.Statement.Assignment.Variable(receiver.variable, value).also {
                    it.context = ast.context
                    it.context.firstOrNull()?.let { context.inputs.removeLast() }
                }
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
                RhovasIr.Statement.Assignment.Property(receiver.receiver, receiver.property, value).also {
                    it.context = ast.context
                    it.context.firstOrNull()?.let { context.inputs.removeLast() }
                }
            }
            is RhovasAst.Expression.Access.Index -> {
                val receiver = visit(ast.receiver.receiver)
                val arguments = ast.receiver.arguments.map { visit(it) }
                val value = visit(ast.value)
                val method = receiver.type.methods["[]=", arguments.map { it.type } + listOf(value.type)] ?: throw error(
                    ast,
                    "Unresolved method.",
                    "The signature []=(${(arguments.map { it.type } + listOf(value.type)).joinToString(", ")}) could not be resolved to a method in ${receiver.type.base.name}.",
                )
                RhovasIr.Statement.Assignment.Index(receiver, method, arguments, value).also {
                    it.context = ast.context
                    it.context.firstOrNull()?.let { context.inputs.removeLast() }
                }
            }
            else -> throw AssertionError()
        }
    }

    override fun visit(ast: RhovasAst.Statement.If): RhovasIr.Statement.If {
        ast.context.firstOrNull()?.let { context.inputs.addLast(it) }
        val condition = visit(ast.condition)
        require(condition.type.isSubtypeOf(Library.TYPES["Boolean"]!!)) { error(
            ast.condition,
            "Invalid if condition type.",
            "An if statement requires the condition to be type Boolean, but received ${condition.type}.",
        ) }
        val thenStatement = analyze(context.child()) {
            visit(ast.thenStatement)
        }
        val elseStatement = analyze(context.child()) {
            ast.elseStatement?.let { visit(it) }
        }
        context.merge()
        return RhovasIr.Statement.If(condition, thenStatement, elseStatement).also {
            it.context = ast.context
            it.context.firstOrNull()?.let { context.inputs.removeLast() }
        }
    }

    override fun visit(ast: RhovasAst.Statement.Match.Conditional): RhovasIr {
        ast.context.firstOrNull()?.let { context.inputs.addLast(it) }
        fun visitCondition(ast: RhovasAst.Expression): RhovasIr.Expression {
            val condition = visit(ast)
            require(condition.type.isSubtypeOf(Library.TYPES["Boolean"]!!)) { error(
                ast,
                "Invalid match condition type.",
                "A conditional match statement requires the condition to be type Boolean, but received ${condition.type}.",
            ) }
            return condition
        }
        val cases = ast.cases.map {
            analyze(context.child()) {
                it.first.context.firstOrNull()?.let { context.inputs.addLast(it) }
                val condition = visitCondition(it.first)
                val statement = visit(it.second)
                it.first.context.firstOrNull()?.let { context.inputs.removeLast() }
                Pair(condition, statement)
            }
        }
        val elseCase = analyze(context.child()) {
            ast.elseCase?.let {
                (it.first ?: it.second).context.firstOrNull()?.let { context.inputs.addLast(it) }
                val condition = it.first?.let { visitCondition(it) }
                val statement = visit(it.second)
                (it.first ?: it.second).context.firstOrNull()?.let { context.inputs.removeLast() }
                Pair(condition, statement)
            }
        }
        context.merge()
        return RhovasIr.Statement.Match.Conditional(cases, elseCase).also {
            it.context = ast.context
            it.context.firstOrNull()?.let { context.inputs.removeLast() }
        }
    }

    override fun visit(ast: RhovasAst.Statement.Match.Structural): RhovasIr {
        ast.context.firstOrNull()?.let { context.inputs.add(it) }
        val argument = visit(ast.argument)
        //TODO: Typecheck patterns
        val cases = ast.cases.map {
            analyze(context.child()) {
                it.first.context.firstOrNull()?.let { context.inputs.addLast(it) }
                val pattern = visit(it.first)
                val statement = visit(it.second)
                it.first.context.firstOrNull()?.let { context.inputs.removeLast() }
                Pair(pattern, statement)
            }
        }
        val elseCase = ast.elseCase?.let {
            analyze(context.child()) {
                (it.first ?: it.second).context.firstOrNull()?.let { context.inputs.addLast(it) }
                val pattern = it.first?.let { visit(it) }
                val statement = visit(it.second)
                (it.first ?: it.second).context.firstOrNull()?.let { context.inputs.removeLast() }
                Pair(pattern, statement)
            }
        }
        context.merge()
        return RhovasIr.Statement.Match.Structural(argument, cases, elseCase).also {
            it.context = ast.context
            it.context.firstOrNull()?.let { context.inputs.removeLast() }
        }
    }

    override fun visit(ast: RhovasAst.Statement.For): RhovasIr.Statement.For {
        ast.context.firstOrNull()?.let { context.inputs.addLast(it) }
        val argument = visit(ast.argument)
        //TODO: Iterable type
        require(argument.type.isSubtypeOf(Library.TYPES["List"]!!)) { error(
            ast.argument,
            "Invalid for loop argument type.",
            "A for loop requires the argument to be type List, but received ${argument.type}.",
        ) }
        //TODO: Proper generic type access
        val type = argument.type.methods["get", listOf(Library.TYPES["Integer"]!!)]!!.returns
        return analyze {
            //TODO: Generic types
            context.scope.variables.define(Variable.Local(ast.name, type, false))
            val previous = context.labels.putIfAbsent(null, false)
            val body = visit(ast.body)
            if (previous != null) {
                context.labels[null] = previous
            } else {
                context.labels.remove(null)
            }
            //TODO: Validate jump context
            context.jumps.clear()
            RhovasIr.Statement.For(ast.name, argument, body).also {
                it.context = ast.context
                it.context.firstOrNull()?.let { context.inputs.removeLast() }
            }
        }
    }

    override fun visit(ast: RhovasAst.Statement.While): RhovasIr.Statement.While {
        ast.context.firstOrNull()?.let { context.inputs.addLast(it) }
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
        return RhovasIr.Statement.While(condition, body).also {
            it.context = ast.context
            it.context.firstOrNull()?.let { context.inputs.removeLast() }
        }
    }

    override fun visit(ast: RhovasAst.Statement.Try): RhovasIr.Statement.Try {
        ast.context.firstOrNull()?.let { context.inputs.addLast(it) }
        val body = visit(ast.body)
        //TODO: Validate thrown exceptions
        val catches = ast.catches.map { visit(it) }
        //TODO: Validate special control flow (and spec)
        val finallyStatement = ast.finallyStatement?.let { visit(it) }
        return RhovasIr.Statement.Try(body, catches, finallyStatement).also {
            it.context = ast.context
            it.context.firstOrNull()?.let { context.inputs.removeLast() }
        }
    }

    override fun visit(ast: RhovasAst.Statement.Try.Catch): RhovasIr.Statement.Try.Catch {
        ast.context.firstOrNull()?.let { context.inputs.addLast(it) }
        return analyze {
            //TODO: Catch type
            context.scope.variables.define(Variable.Local(ast.name, Library.TYPES["Exception"]!!, false))
            val body = visit(ast.body)
            RhovasIr.Statement.Try.Catch(ast.name, body).also {
                it.context = ast.context
                it.context.firstOrNull()?.let { context.inputs.removeLast() }
            }
        }
    }

    override fun visit(ast: RhovasAst.Statement.With): RhovasIr.Statement.With {
        ast.context.firstOrNull()?.let { context.inputs.addLast(it) }
        val argument = visit(ast.argument)
        return analyze {
            ast.name?.let { context.scope.variables.define(Variable.Local(ast.name, argument.type, false)) }
            val body = visit(ast.body)
            RhovasIr.Statement.With(ast.name, argument, body).also {
                it.context = ast.context
                it.context.firstOrNull()?.let { context.inputs.removeLast() }
            }
        }
    }

    override fun visit(ast: RhovasAst.Statement.Label): RhovasIr.Statement.Label {
        ast.context.firstOrNull()?.let { context.inputs.addLast(it) }
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
        return RhovasIr.Statement.Label(ast.label, statement).also {
            it.context = ast.context
            it.context.firstOrNull()?.let { context.inputs.removeLast() }
        }
    }

    override fun visit(ast: RhovasAst.Statement.Break): RhovasIr.Statement.Break {
        ast.context.firstOrNull()?.let { context.inputs.addLast(it) }
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
        return RhovasIr.Statement.Break(ast.label).also {
            it.context = ast.context
            it.context.firstOrNull()?.let { context.inputs.removeLast() }
        }
    }

    override fun visit(ast: RhovasAst.Statement.Continue): RhovasIr.Statement.Continue {
        ast.context.firstOrNull()?.let { context.inputs.addLast(it) }
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
        return RhovasIr.Statement.Continue(ast.label).also {
            it.context = ast.context
            it.context.firstOrNull()?.let { context.inputs.removeLast() }
        }
    }

    override fun visit(ast: RhovasAst.Statement.Return): RhovasIr.Statement.Return {
        ast.context.firstOrNull()?.let { context.inputs.addLast(it) }
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
        return RhovasIr.Statement.Return(value).also {
            it.context = ast.context
            it.context.firstOrNull()?.let { context.inputs.removeLast() }
        }
    }

    override fun visit(ast: RhovasAst.Statement.Throw): RhovasIr.Statement.Throw {
        ast.context.firstOrNull()?.let { context.inputs.addLast(it) }
        val exception = visit(ast.exception)
        require(exception.type.isSubtypeOf(Library.TYPES["Exception"]!!)) { error(
            ast.exception,
            "Invalid throw expression type.",
            "An throw statement requires the expression to be type Exception, but received ${exception.type}.",
        ) }
        return RhovasIr.Statement.Throw(exception).also {
            it.context = ast.context
            it.context.firstOrNull()?.let { context.inputs.removeLast() }
        }
    }

    override fun visit(ast: RhovasAst.Statement.Assert): RhovasIr.Statement.Assert {
        ast.context.firstOrNull()?.let { context.inputs.addLast(it) }
        val condition = visit(ast.condition)
        require(condition.type.isSubtypeOf(Library.TYPES["Boolean"]!!)) { error(
            ast.condition,
            "Invalid assert condition type.",
            "An assert statement requires the condition to be type Boolean, but received ${condition.type}.",
        ) }
        val message = ast.message?.let { visit(it) }
        require(message == null || message.type.isSubtypeOf(Library.TYPES["String"]!!)) { error(
            ast.message!!,
            "Invalid assert message type.",
            "An assert statement requires the message to be type String, but received ${message!!.type}.",
        ) }
        return RhovasIr.Statement.Assert(condition, message).also {
            it.context = ast.context
            it.context.firstOrNull()?.let { context.inputs.removeLast() }
        }
    }

    override fun visit(ast: RhovasAst.Statement.Require): RhovasIr.Statement.Require {
        ast.context.firstOrNull()?.let { context.inputs.addLast(it) }
        val condition = visit(ast.condition)
        require(condition.type.isSubtypeOf(Library.TYPES["Boolean"]!!)) { error(
            ast.condition,
            "Invalid require condition type.",
            "A require statement requires the condition to be type Boolean, but received ${condition.type}.",
        ) }
        val message = ast.message?.let { visit(it) }
        require(message == null || message.type.isSubtypeOf(Library.TYPES["String"]!!)) { error(
            ast.message!!,
            "Invalid require message type.",
            "A require statement requires the message to be type String, but received ${message!!.type}.",
        ) }
        return RhovasIr.Statement.Require(condition, message).also {
            it.context = ast.context
            it.context.firstOrNull()?.let { context.inputs.removeLast() }
        }
    }

    override fun visit(ast: RhovasAst.Statement.Ensure): RhovasIr.Statement.Ensure {
        ast.context.firstOrNull()?.let { context.inputs.addLast(it) }
        val condition = visit(ast.condition)
        require(condition.type.isSubtypeOf(Library.TYPES["Boolean"]!!)) { error(
            ast.condition,
            "Invalid ensure condition type.",
            "An ensure statement requires the condition to be type Boolean, but received ${condition.type}.",
        ) }
        val message = ast.message?.let { visit(it) }
        require(message == null || message.type.isSubtypeOf(Library.TYPES["String"]!!)) { error(
            ast.message!!,
            "Invalid ensure message type.",
            "An ensure statement requires the message to be type String, but received ${message!!.type}.",
        ) }
        return RhovasIr.Statement.Ensure(condition, message).also {
            it.context = ast.context
            it.context.firstOrNull()?.let { context.inputs.removeLast() }
        }
    }

    private fun visit(ast: RhovasAst.Expression): RhovasIr.Expression {
        return super.visit(ast) as RhovasIr.Expression
    }

    override fun visit(ast: RhovasAst.Expression.Literal.Scalar): RhovasIr {
        val type = when (ast.value) {
            null -> Library.TYPES["Null"]!!
            is Boolean -> Library.TYPES["Boolean"]!!
            is BigInteger -> Library.TYPES["Integer"]!!
            is BigDecimal -> Library.TYPES["Decimal"]!!
            is RhovasAst.Atom -> Library.TYPES["Atom"]!!
            else -> throw AssertionError()
        }
        return RhovasIr.Expression.Literal.Scalar(ast.value, type).also {
            it.context = ast.context
        }
    }

    override fun visit(ast: RhovasAst.Expression.Literal.String): RhovasIr {
        val arguments = ast.arguments.map { visit(it) }
        val type = Library.TYPES["String"]!!
        return RhovasIr.Expression.Literal.String(ast.literals, arguments, type).also {
            it.context = ast.context
        }
    }

    override fun visit(ast: RhovasAst.Expression.Literal.List): RhovasIr {
        ast.context.firstOrNull()?.let { context.inputs.addLast(it) }
        val elements = ast.elements.map { visit(it) }
        val type = Type.Reference(Library.TYPES["List"]!!.base, listOf(Library.TYPES["Dynamic"]!!))
        return RhovasIr.Expression.Literal.List(elements, type).also {
            it.context = ast.context
            it.context.firstOrNull()?.let { context.inputs.removeLast() }
        }
    }

    override fun visit(ast: RhovasAst.Expression.Literal.Object): RhovasIr {
        ast.context.firstOrNull()?.let { context.inputs.addLast(it) }
        val properties = ast.properties.mapValues { visit(it.value) }
        val type = Library.TYPES["Object"]!!
        return RhovasIr.Expression.Literal.Object(properties, type).also {
            it.context = ast.context
            it.context.firstOrNull()?.let { context.inputs.removeLast() }
        }
    }

    override fun visit(ast: RhovasAst.Expression.Group): RhovasIr.Expression.Group {
        ast.context.firstOrNull()?.let { context.inputs.addLast(it) }
        val expression = visit(ast.expression)
        return RhovasIr.Expression.Group(expression).also {
            it.context = ast.context
            it.context.firstOrNull()?.let { context.inputs.removeLast() }
        }
    }

    override fun visit(ast: RhovasAst.Expression.Unary): RhovasIr.Expression.Unary {
        ast.context.firstOrNull()?.let { context.inputs.addLast(it) }
        val expression = visit(ast.expression)
        val method = expression.type.methods[ast.operator, listOf()] ?: throw error(
            ast,
            "Undefined method.",
            "The method op${ast.operator}() is not defined in ${expression.type.base.name}.",
        )
        return RhovasIr.Expression.Unary(ast.operator, expression, method).also {
            it.context = ast.context
            it.context.firstOrNull()?.let { context.inputs.removeLast() }
        }
    }

    override fun visit(ast: RhovasAst.Expression.Binary): RhovasIr.Expression.Binary {
        val left = visit(ast.left)
        val right = visit(ast.right)
        ast.context.firstOrNull()?.let { context.inputs.addLast(it) }
        val (type, method) = when (ast.operator) {
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
                Pair(Library.TYPES["Boolean"]!!, null)
            }
            "==", "!=" -> {
                //TODO: Equatable<T> interface
                val method = left.type.methods["==", listOf(left.type)] ?: throw error(
                    ast,
                    "Undefined method.",
                    "The method op==(${left.type}) is not defined in ${left.type.base.name}.",
                )
                Pair(Library.TYPES["Boolean"]!!, method)
            }
            "===", "!==" -> {
                Pair(Library.TYPES["Boolean"]!!, null)
            }
            "<", ">", "<=", ">=" -> {
                val method = left.type.methods["<=>", listOf(right.type)] ?: throw error(
                    ast,
                    "Unresolved method.",
                    "The signature op<=>(${listOf(right.type)} could not be resolved to a method in ${left.type.base.name}.",
                )
                Pair(Library.TYPES["Boolean"]!!, method)
            }
            "+", "-", "*", "/" -> {
                val method = left.type.methods[ast.operator, listOf(right.type)] ?: throw error(
                    ast,
                    "Unresolved method.",
                    "The signature op${ast.operator}(${listOf(right.type)} could not be resolved to a method in ${left.type.base.name}.",
                )
                Pair(method.returns, method)
            }
            else -> throw AssertionError()
        }
        return RhovasIr.Expression.Binary(ast.operator, left, right, method, type).also {
            it.context = ast.context
            it.context.firstOrNull()?.let { context.inputs.removeLast() }
        }
    }

    override fun visit(ast: RhovasAst.Expression.Access.Variable): RhovasIr.Expression.Access.Variable {
        ast.context.firstOrNull()?.let { context.inputs.addLast(it) }
        //TODO: Variable.Local handling
        val variable = when(val variable = context.scope.variables[ast.name]) {
            is Variable.Local -> variable
            is Variable.Local.Runtime -> variable.variable
            else -> throw error(
                ast,
                "Undefined variable.",
                "The variable ${ast.name} is not defined in the current scope."
            )
        }
        return RhovasIr.Expression.Access.Variable(variable).also {
            it.context = ast.context
            it.context.firstOrNull()?.let { context.inputs.removeLast() }
        }
    }

    override fun visit(ast: RhovasAst.Expression.Access.Property): RhovasIr.Expression.Access.Property {
        ast.context.firstOrNull()?.let { context.inputs.addLast(it) }
        val receiver = visit(ast.receiver)
        val method = receiver.type.properties[ast.name] ?: throw error(
            ast,
            "Undefined property.",
            "The property getter ${ast.name}() is not defined in ${receiver.type.base.name}.",
        )
        //TODO: Coalesce typechecking (requires nullable types)
        return RhovasIr.Expression.Access.Property(receiver, method, ast.coalesce).also {
            it.context = ast.context
            it.context.firstOrNull()?.let { context.inputs.removeLast() }
        }
    }

    override fun visit(ast: RhovasAst.Expression.Access.Index): RhovasIr.Expression.Access.Index {
        ast.context.firstOrNull()?.let { context.inputs.addLast(it) }
        val receiver = visit(ast.receiver)
        val arguments = ast.arguments.map { visit(it) }
        val method = receiver.type.methods["[]", arguments.map { it.type }] ?: throw error(
            ast,
            "Unresolved method.",
            "The signature [](${arguments.map { it.type }.joinToString(", ")}) could not be resolved to a method in ${receiver.type.base.name}.",
        )
        return RhovasIr.Expression.Access.Index(receiver, method, arguments).also {
            it.context = ast.context
            it.context.firstOrNull()?.let { context.inputs.removeLast() }
        }
    }

    override fun visit(ast: RhovasAst.Expression.Invoke.Function): RhovasIr.Expression.Invoke.Function {
        ast.context.firstOrNull()?.let { context.inputs.addLast(it) }
        val arguments = ast.arguments.map { visit(it) }
        val function = context.scope.functions[ast.name, arguments.map { it.type }] as Function.Definition? ?: throw error(
            ast,
            "Unresolved function.",
            "The signature ${ast.name}(${arguments.map { it.type }.joinToString(", ")}) could not be resolved to a function in the current scope.",
        )
        return RhovasIr.Expression.Invoke.Function(function, arguments).also {
            it.context = ast.context
            it.context.firstOrNull()?.let { context.inputs.removeLast() }
        }
    }

    override fun visit(ast: RhovasAst.Expression.Invoke.Method): RhovasIr.Expression.Invoke.Method {
        ast.context.firstOrNull()?.let { context.inputs.addLast(it) }
        val receiver = visit(ast.receiver)
        val arguments = ast.arguments.map { visit(it) }
        val method = receiver.type.methods[ast.name, arguments.map { it.type }] ?: throw error(
            ast,
            "Unresolved method.",
            "The signature ${ast.name}(${arguments.map { it.type }.joinToString(", ")}) could not be resolved to a method in ${receiver.type.base.name}.",
        )
        //TODO: Coalesce typechecking (requires nullable types)
        return RhovasIr.Expression.Invoke.Method(receiver, method, ast.coalesce, ast.cascade, arguments).also {
            it.context = ast.context
            it.context.firstOrNull()?.let { context.inputs.removeLast() }
        }
    }

    override fun visit(ast: RhovasAst.Expression.Invoke.Pipeline): RhovasIr.Expression.Invoke.Pipeline {
        ast.context.firstOrNull()?.let { context.inputs.addLast(it) }
        val receiver = visit(ast.receiver)
        val qualifier = ast.qualifier?.let { visit(it) as RhovasIr.Expression.Access }
        val arguments = ast.arguments.map { visit(it) }
        val function = if (qualifier == null) {
            context.scope.functions[ast.name, listOf(receiver.type) + arguments.map { it.type }] as Function.Definition? ?: throw error(
                ast,
                "Unresolved function.",
                "The signature ${ast.name}(${(listOf(receiver.type) + arguments.map { it.type }).joinToString(", ")}) could not be resolved to a function in the current scope.",
            )
        } else {
            qualifier.type.functions[ast.name, listOf(receiver.type) + arguments.map { it.type }] ?: throw error(
                ast,
                "Unresolved function.",
                "The signature ${ast.name}(${(listOf(receiver.type) + arguments.map { it.type }).joinToString(", ")}) could not be resolved to a function in ${qualifier.type.base.name}.",
            )
        }
        //TODO: Coalesce typechecking (requires nullable types)
        return RhovasIr.Expression.Invoke.Pipeline(receiver, function, ast.coalesce, ast.cascade, arguments).also {
            it.context = ast.context
            it.context.firstOrNull()?.let { context.inputs.removeLast() }
        }
    }

    override fun visit(ast: RhovasAst.Expression.Lambda): RhovasIr.Expression.Lambda {
        ast.context.firstOrNull()?.let { context.inputs.addLast(it) }
        //TODO: Validate using same requirements as function statements
        //TODO: Type inference/unification for parameter types
        val parameters = ast.parameters.map { Pair(it.first, it.second?.let { visit(it) }) }
        val function = Function.Definition("lambda", parameters.map { Pair(it.first, it.second?.type ?: Library.TYPES["Dynamic"]!!) }, Library.TYPES["Dynamic"]!!)
        return analyze(context.child().copy(function = function)) {
            if (parameters.isNotEmpty()) {
                parameters.forEach { context.scope.variables.define(Variable.Local(it.first, Library.TYPES["Dynamic"]!!, false)) }
            } else {
                context.scope.variables.define(Variable.Local("val", Library.TYPES["Dynamic"]!!, false))
            }
            val body = visit(ast.body)
            val type = Type.Reference(Library.TYPES["Lambda"]!!.base, listOf(Library.TYPES["Dynamic"]!!, Library.TYPES["Dynamic"]!!))
            RhovasIr.Expression.Lambda(parameters, body, type).also {
                it.context = ast.context
                it.context.firstOrNull()?.let { context.inputs.removeLast() }
            }
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
        ast.context.firstOrNull()?.let { context.inputs.addLast(it) }
        //TODO: Delegation of AST analysis
        val source = ast.ast as? DslAst.Source ?: throw error(
            ast,
            "Invalid DSL AST.",
            "The AST of type " + ast.ast + " is not supported by the analyzer.",
        )
        val function = context.scope.functions[ast.name, listOf(Type.Reference(Library.TYPES["List"]!!.base, listOf(Library.TYPES["String"]!!)), Type.Reference(Library.TYPES["List"]!!.base, listOf(Library.TYPES["Any"]!!)))] as Function.Definition? ?: throw error(
            ast,
            "Undefined DSL transformer.",
            "The DSL ${ast.name} requires a transformer function ${ast.name}(List<String>, List<Any>).",
        )
        val literals = RhovasIr.Expression.Literal.List(
            source.literals.map { RhovasIr.Expression.Literal.String(listOf(it), listOf(), Library.TYPES["String"]!!) },
            Type.Reference(Library.TYPES["List"]!!.base, listOf(Library.TYPES["String"]!!)),
        )
        val arguments = RhovasIr.Expression.Literal.List(
            source.arguments.map { visit(it as RhovasAst.Expression) },
            Type.Reference(Library.TYPES["List"]!!.base, listOf(Library.TYPES["Dynamic"]!!)),
        )
        //TODO: Compile time invocation
        return RhovasIr.Expression.Invoke.Function(function, listOf(literals, arguments)).also {
            it.context = ast.context
            it.context.firstOrNull()?.let { context.inputs.removeLast() }
        }
    }

    override fun visit(ast: RhovasAst.Expression.Interpolation): RhovasIr.Expression.Interpolation {
        val expression = visit(ast.expression)
        return RhovasIr.Expression.Interpolation(expression)
    }

    private fun visit(ast: RhovasAst.Pattern): RhovasIr.Pattern {
        return super.visit(ast) as RhovasIr.Pattern
    }

    override fun visit(ast: RhovasAst.Pattern.Variable): RhovasIr.Pattern.Variable {
        ast.context.firstOrNull()?.let { context.inputs.addLast(it) }
        //TODO: Validate variable name
        //TODO: Infer type
        val variable = if (ast.name != "_") Variable.Local(ast.name, Library.TYPES["Dynamic"]!!, false) else null
        variable?.let { context.scope.variables.define(it) }
        return RhovasIr.Pattern.Variable(variable).also {
            it.context = ast.context
            it.context.firstOrNull()?.let { context.inputs.removeLast() }
        }
    }

    override fun visit(ast: RhovasAst.Pattern.Value): RhovasIr.Pattern.Value {
        //TODO: Value typechecking
        val value = visit(ast.value)
        return RhovasIr.Pattern.Value(value).also {
            it.context = ast.context
        }
    }

    override fun visit(ast: RhovasAst.Pattern.Predicate): RhovasIr.Pattern.Predicate {
        ast.context.firstOrNull()?.let { context.inputs.addLast(it) }
        val pattern = visit(ast.pattern)
        //TODO: Bind variables
        val predicate = analyze(context.child()) {
            context.scope.variables.define(Variable.Local("val", pattern.type, false))
            visit(ast.predicate)
        }
        require(predicate.type.isSubtypeOf(Library.TYPES["Boolean"]!!)) { error(
            ast.predicate,
            "Invalid pattern predicate type.",
            "A predicate pattern requires the predicate to be type Boolean, but received ${predicate.type}.",
        ) }
        return RhovasIr.Pattern.Predicate(pattern, predicate).also {
            it.context = ast.context
            it.context.firstOrNull()?.let { context.inputs.removeLast() }
        }
    }

    override fun visit(ast: RhovasAst.Pattern.OrderedDestructure): RhovasIr.Pattern.OrderedDestructure {
        ast.context.firstOrNull()?.let { context.inputs.addLast(it) }
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
                RhovasIr.Pattern.VarargDestructure(pattern, ast.operator, Type.Reference(Library.TYPES["List"]!!.base, listOf(Library.TYPES["Dynamic"]!!))).also {
                    it.context = ast.context
                }
            } else {
                visit(it.value)
            }
        }
        return RhovasIr.Pattern.OrderedDestructure(patterns, Type.Reference(Library.TYPES["List"]!!.base, listOf(Library.TYPES["Dynamic"]!!))).also {
            it.context = ast.context
            it.context.firstOrNull()?.let { context.inputs.removeLast() }
        }
    }

    override fun visit(ast: RhovasAst.Pattern.NamedDestructure): RhovasIr.Pattern.NamedDestructure {
        ast.context.firstOrNull()?.let { context.inputs.addLast(it) }
        //TODO: Value typechecking
        //TODO: Validate keys/variable names
        var vararg = false
        val patterns = ast.patterns.withIndex().map {
            val pattern = if (it.value.second is RhovasAst.Pattern.VarargDestructure) {
                //TODO: Consider requiring varargs as the last pattern
                require(!vararg) { error(
                    it.value.second!!,
                    "Invalid multiple varargs.",
                    "A named destructure requires no more than one vararg pattern.",
                ) }
                vararg = true
                val ast = it.value.second as RhovasAst.Pattern.VarargDestructure
                val pattern = ast.pattern?.let { visit(it) }
                RhovasIr.Pattern.VarargDestructure(pattern, ast.operator, Library.TYPES["Object"]!!).also {
                    it.context = ast.context
                }
            } else if (it.value.second != null) {
                visit(it.value.second!!)
            } else {
                context.scope.variables.define(Variable.Local(it.value.first, Library.TYPES["Dynamic"]!!, false))
                null
            }
            Pair(it.value.first, pattern)
        }
        return RhovasIr.Pattern.NamedDestructure(patterns, Library.TYPES["Object"]!!).also {
            it.context = ast.context
            it.context.firstOrNull()?.let { context.inputs.removeLast() }
        }
    }

    override fun visit(ast: RhovasAst.Pattern.TypedDestructure): RhovasIr.Pattern.TypedDestructure {
        val type = visit(ast.type)
        //TODO: Value typechecking
        val pattern = ast.pattern?.let { visit(it) }
        return RhovasIr.Pattern.TypedDestructure(type.type, pattern).also {
            it.context = ast.context
        }
    }

    override fun visit(ast: RhovasAst.Pattern.VarargDestructure): RhovasIr.Pattern.VarargDestructure {
        throw AssertionError()
    }

    override fun visit(ast: RhovasAst.Type): RhovasIr.Type {
        ast.context.firstOrNull()?.let { context.inputs.addLast(it) }
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
        return RhovasIr.Type(type).also {
            it.context = ast.context
            it.context.firstOrNull()?.let { context.inputs.removeLast() }
        }
    }

}
