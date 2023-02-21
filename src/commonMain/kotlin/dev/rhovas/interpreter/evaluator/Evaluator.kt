package dev.rhovas.interpreter.evaluator

import com.ionspin.kotlin.bignum.integer.BigInteger
import dev.rhovas.interpreter.analyzer.rhovas.RhovasIr
import dev.rhovas.interpreter.environment.*
import dev.rhovas.interpreter.environment.Function
import dev.rhovas.interpreter.library.Library
import dev.rhovas.interpreter.parser.Input

class Evaluator(private var scope: Scope.Definition) : RhovasIr.Visitor<Object> {

    private var label: String? = null
    private lateinit var patternState: PatternState
    private var stacktrace = ArrayDeque<StackFrame>().also {
        it.addLast(StackFrame("Source", Input.Range(0, 1, 0, 0)))
    }

    override fun visit(ir: RhovasIr.Source): Object {
        ir.statements.forEach { visit(it) }
        return Object(Type.VOID, Unit)
    }

    override fun visit(ir: RhovasIr.Import): Object {
        throw AssertionError()
    }

    override fun visit(ir: RhovasIr.Component.Struct): Object {
        //TODO(#11): Component declaration/definition handling
        if (!scope.types.isDefined(ir.type.base.name, true)) {
            scope.types.define(ir.type, ir.type.base.name)
        }
        ir.members.forEach { visit(it) }
        val current = scope
        val initializer = ir.type.base.scope.functions["", 1].first { it.parameters.first().type.isSubtypeOf(Type.OBJECT) }
        initializer.implementation = { arguments ->
            scoped(Scope.Definition(current)) {
                val fields = arguments[0].value as Map<String, Object>
                Object(ir.type, ir.members.filterIsInstance<RhovasIr.Member.Property>().associate {
                    Pair(it.getter.name, fields[it.getter.name] ?: it.value?.let { visit(it) } ?: Object(Type.NULL, null))
                })
            }
        }
        //TODO(#14): Should inherit Struct.to(String)
        val toString = ir.type.base.scope.functions["to", 2].first { it.parameters.first().type.isSubtypeOf(ir.type) && it.parameters.last().type.isSubtypeOf(Type.TYPE[Type.STRING]) }
        toString.implementation = { arguments ->
            val instance = arguments[0].value as Map<String, Object>
            val fields = Object(Type.OBJECT, instance).methods.toString()
            Object(Type.STRING, "${ir.type.base.name} ${fields}")
        }
        return Object(Type.VOID, Unit)
    }

    override fun visit(ir: RhovasIr.Component.Class): Object {
        scope.types.define(ir.type, ir.type.base.name)
        ir.members.forEach { visit(it) }
        return Object(Type.VOID, Unit)
    }

    override fun visit(ir: RhovasIr.Member.Property): Object {
        ir.getter.implementation = { arguments ->
            val instance = arguments[0].value as MutableMap<String, Object>
            instance[ir.getter.name]!!
        }
        ir.setter?.implementation = { arguments ->
            val instance = arguments[0].value as MutableMap<String, Object>
            instance[ir.getter.name] = arguments[1]
            Object(Type.VOID, Unit)
        }
        return Object(Type.VOID, Unit)
    }

    override fun visit(ir: RhovasIr.Member.Initializer): Object {
        val current = scope
        ir.function.implementation = { arguments ->
            scoped(Scope.Definition(current)) {
                val instance = Variable.Definition(Variable.Declaration("this", ir.function.returns, false))
                instance.value = Object(instance.type, mutableMapOf<String, Object>())
                scope.variables.define(instance)
                for (i in ir.function.parameters.indices) {
                    val parameter = Variable.Definition(ir.function.parameters[i])
                    parameter.value = arguments[i]
                    scope.variables.define(parameter)
                }
                try {
                    visit(ir.block)
                } catch (e: Throw) {
                    require(ir.function.throws.any { e.exception.type.isSubtypeOf(it) }) { error(
                        ir,
                        "Uncaught exception.",
                        "An exception of type ${e.exception.type} was thrown but not declared: ${e.exception.methods.toString()}"
                    ) }
                    throw e
                } catch (ignored: Return) {}
                instance.value
            }
        }
        return Object(Type.VOID, Unit)
    }

    override fun visit(ir: RhovasIr.Member.Method): Object {
        visit(ir.function)
        return Object(Type.VOID, Unit)
    }

    override fun visit(ir: RhovasIr.Statement.Component): Object {
        visit(ir.component)
        return Object(Type.VOID, Unit)
    }

    override fun visit(ir: RhovasIr.Statement.Initializer): Object {
        val fields = visit(ir.initializer).value as MutableMap<String, Object>
        val instance = scope.variables["this"]!!.value.value as MutableMap<String, Object>
        instance.putAll(fields)
        return Object(Type.VOID, Unit)
    }

    override fun visit(ir: RhovasIr.Statement.Expression): Object {
        visit(ir.expression)
        return Object(Type.VOID, Unit)
    }

    override fun visit(ir: RhovasIr.Statement.Declaration.Variable): Object {
        val variable = ir.variable as? Variable.Definition ?: Variable.Definition(ir.variable as Variable.Declaration).also { scope.variables.define(it) }
        variable.value = ir.value?.let { visit(it) } ?: Object(Type.NULL, null)
        return Object(Type.VOID, Unit)
    }

    override fun visit(ir: RhovasIr.Statement.Declaration.Function): Object {
        val current = scope
        val function = ir.function as? Function.Definition ?: Function.Definition(ir.function as Function.Declaration).also { scope.functions.define(it) }
        function.implementation = { arguments ->
            scoped(Scope.Definition(current)) {
                for (i in ir.function.parameters.indices) {
                    val parameter = Variable.Definition(ir.function.parameters[i])
                    parameter.value = arguments[i]
                    scope.variables.define(parameter)
                }
                try {
                    visit(ir.block)
                    Object(Type.VOID, Unit)
                } catch (e: Throw) {
                    require(ir.function.throws.any { e.exception.type.isSubtypeOf(it) }) { error(
                        ir,
                        "Uncaught exception.",
                        "An exception of type ${e.exception.type} was thrown but not declared: ${e.exception.methods.toString()}"
                    ) }
                    throw e
                } catch (e: Return) {
                    e.value ?: Object(Type.VOID, Unit)
                }
            }
        }
        return Object(Type.VOID, Unit)
    }

    override fun visit(ir: RhovasIr.Statement.Assignment.Variable): Object {
        val variable = scope.variables[ir.variable.name]!!
        variable.value = visit(ir.value)
        return Object(Type.VOID, Unit)
    }

    override fun visit(ir: RhovasIr.Statement.Assignment.Property): Object {
        val receiver = visit(ir.receiver)
        val value = visit(ir.value)
        val property = receiver[ir.property] ?: throw error(
            ir,
            "Undefined property.",
            "The property ${ir.property.name} is not defined in ${receiver.type.base.name}.",
        )
        val method = property.setter ?: throw error(
            ir,
            "Unassignable property.",
            "The property ${receiver.type.base.name}.${ir.property.name} does not support assignment.",
        )
        require(value.type.isSubtypeOf(method.parameters[0].type)) { error(
            ir.value,
            "Invalid property value type.",
            "The property ${receiver.type.base.name}.${method.name} requires the value to be type ${method.parameters[0].type}, but received ${value.type}.",
        ) }
        trace("${receiver.type.base.name}.${method.name}(${method.parameters.map { it.type }.joinToString(", ")})", ir.context.firstOrNull()) {
            method.invoke(listOf(value))
        }
        return Object(Type.VOID, Unit)
    }

    override fun visit(ir: RhovasIr.Statement.Assignment.Index): Object {
        val receiver = visit(ir.receiver)
        val arguments = ir.arguments.map { visit(it) } + listOf(visit(ir.value))
        val method = receiver[ir.method]  ?: throw error(
            ir,
            "Undefined method.",
            "The method ${ir.method.name}(${ir.method.parameters.map { it.type }.joinToString(", ")}) is not defined in ${receiver.type.base.name}.",
        )
        for (i in arguments.indices) {
            require(arguments[i].type.isSubtypeOf(method.parameters[i].type)) { error(
                ir.arguments.getOrNull(i) ?: ir.value,
                "Invalid method argument type.",
                "The method ${receiver.type.base.name}.${method.name}(${method.parameters.map { it.type }.joinToString(", ")}) requires argument ${i} to be type ${method.parameters[i].type}, but received ${arguments[i].type}.",
            ) }
        }
        trace("${receiver.type.base.name}.${method.name}(${method.parameters.map { it.type }.joinToString(", ")})", ir.context.firstOrNull()) {
            method.invoke(arguments)
        }
        return Object(Type.VOID, Unit)
    }

    override fun visit(ir: RhovasIr.Statement.If): Object {
        val condition = visit(ir.condition)
        if (condition.value as Boolean) {
            visit(ir.thenBlock)
        } else if (ir.elseBlock != null) {
            visit(ir.elseBlock)
        }
        return Object(Type.VOID, Unit)
    }

    override fun visit(ir: RhovasIr.Statement.Match.Conditional): Object {
        val case = ir.cases.firstOrNull { visit(it.first).value as Boolean }
            ?: ir.elseCase?.also {
                require(it.first == null || visit(it.first!!).value as Boolean) { error(
                    ir.elseCase.first,
                    "Failed match else assertion.",
                    "A condition match statement requires the else condition to be true.",
                ) }
            }
        case?.let { visit(it.second) }
        return Object(Type.VOID, Unit)
    }

    override fun visit(ir: RhovasIr.Statement.Match.Structural): Object {
        val argument = visit(ir.argument)
        val predicate: (RhovasIr.Pattern) -> Boolean = {
            patternState = PatternState(Scope.Definition(this.scope), argument)
            scoped(patternState.scope) {
                visit(it).value as Boolean
            }
        }
        val case = ir.cases.firstOrNull { predicate.invoke(it.first) }
            ?: ir.elseCase?.also {
                require(predicate.invoke(it.first ?: RhovasIr.Pattern.Variable(Variable.Declaration("_", argument.type, false)))) { error(
                    ir.elseCase.first,
                    "Failed match else assertion.",
                    "A structural match statement requires the else pattern to match.",
                ) }
            }
            ?: throw error(
                ir,
                "Non-exhaustive structural match patterns.",
                "A structural match statements requires the patterns to be exhaustive, but no pattern matched argument ${argument.methods.toString()}.",
            )
        scoped(patternState.scope) {
            visit(case.second)
        }
        return Object(Type.VOID, Unit)
    }

    override fun visit(ir: RhovasIr.Statement.For): Object {
        val iterable = visit(ir.argument)
        val label = this.label
        this.label = null
        for (element in iterable.value as List<Object>) {
            try {
                scoped(Scope.Definition(scope)) {
                    val variable = Variable.Definition(ir.variable)
                    variable.value = element
                    scope.variables.define(variable)
                    visit(ir.block)
                }
            } catch (e: Break) {
                if (e.label != null && e.label != label) {
                    throw e
                }
                break
            } catch (e: Continue) {
                if (e.label != null && e.label != label) {
                    throw e
                }
                continue
            }
        }
        return Object(Type.VOID, Unit)
    }

    override fun visit(ir: RhovasIr.Statement.While): Object {
        val label = this.label
        this.label = null
        while (true) {
            val condition = visit(ir.condition)
            if (condition.value as Boolean) {
                try {
                    scoped(Scope.Definition(scope)) {
                        visit(ir.block)
                    }
                } catch (e: Break) {
                    if (e.label != null && e.label != label) {
                        throw e
                    }
                    break
                } catch (e: Continue) {
                    if (e.label != null && e.label != label) {
                        throw e
                    }
                    continue
                }
            } else {
                break
            }
        }
        return Object(Type.VOID, Unit)
    }

    override fun visit(ir: RhovasIr.Statement.Try): Object {
        try {
            visit(ir.tryBlock)
        } catch (e: Throw) {
            val catch = ir.catchBlocks.firstOrNull { e.exception.type.isSubtypeOf(it.variable.type) }
            if (catch != null) {
                scoped(Scope.Definition(scope)) {
                    val variable = Variable.Definition(catch.variable)
                    variable.value = e.exception
                    scope.variables.define(variable)
                    try {
                        visit(catch.block)
                    } catch (e: Throw) {
                        ir.finallyBlock?.let { visit(it) }
                        throw e
                    }
                }
            } else {
                ir.finallyBlock?.let { visit(it) }
                throw e
            }
        }
        ir.finallyBlock?.let { visit(it) }
        return Object(Type.VOID, Unit)
    }

    override fun visit(ir: RhovasIr.Statement.Try.Catch): Object {
        throw AssertionError()
    }

    override fun visit(ir: RhovasIr.Statement.With): Object {
        val argument = visit(ir.argument)
        return scoped(Scope.Definition(scope)) {
            ir.variable?.let {
                val variable = Variable.Definition(ir.variable)
                variable.value = argument
                scope.variables.define(variable)
            }
            visit(ir.block)
        }
    }

    override fun visit(ir: RhovasIr.Statement.Label): Object {
        val label = label
        this.label = ir.label
        try {
            visit(ir.statement)
        } finally {
            this.label = label
        }
        return Object(Type.VOID, Unit)
    }

    override fun visit(ir: RhovasIr.Statement.Break): Object {
        throw Break(ir.label)
    }

    override fun visit(ir: RhovasIr.Statement.Continue): Object {
        throw Continue(ir.label)
    }

    override fun visit(ir: RhovasIr.Statement.Return): Object {
        throw Return(ir, ir.value?.let { visit(it) })
    }

    override fun visit(ir: RhovasIr.Statement.Throw): Object {
        throw Throw(visit(ir.exception))
    }

    override fun visit(ir: RhovasIr.Statement.Assert): Object {
        val condition = visit(ir.condition)
        require(condition.value as Boolean) { error(
            ir,
            "Failed assertion",
            "The assertion failed" + ir.message?.let { " (${visit(it).value})" }.orEmpty() + ".",
        ) }
        return Object(Type.VOID, Unit)
    }

    override fun visit(ir: RhovasIr.Statement.Require): Object {
        val condition = visit(ir.condition)
        require(condition.value as Boolean) { error(
            ir,
            "Failed precondition assertion.",
            "The precondition assertion failed" + ir.message?.let { " (${visit(it).value})" }.orEmpty() + ".",
        ) }
        return Object(Type.VOID, Unit)
    }

    override fun visit(ir: RhovasIr.Statement.Ensure): Object {
        val condition = visit(ir.condition)
        require(condition.value as Boolean) { error(
            ir,
            "Failed postcondition assertion.",
            "The postcondition assertion failed" + ir.message?.let { " (${visit(it).value})" }.orEmpty() + ".",
        ) }
        return Object(Type.VOID, Unit)
    }

    override fun visit(ir: RhovasIr.Expression.Block): Object {
        ir.statements.forEach { visit(it) }
        val expression = ir.expression?.let { visit(it) }
        return expression ?: Object(Type.VOID, Unit)
    }

    override fun visit(ir: RhovasIr.Expression.Literal.Scalar): Object {
        return Object(ir.type, ir.value)
    }

    override fun visit(ir: RhovasIr.Expression.Literal.String): Object {
        val builder = StringBuilder()
        ir.arguments.indices.forEach {
            builder.append(ir.literals[it])
            builder.append(visit(ir.arguments[it]).methods.toString())
        }
        builder.append(ir.literals.last())
        return Object(Type.STRING, builder.toString())
    }

    override fun visit(ir: RhovasIr.Expression.Literal.List): Object {
        val value = ir.elements.map { visit(it) }
        return Object(ir.type, value)
    }

    override fun visit(ir: RhovasIr.Expression.Literal.Object): Object {
        val value = ir.properties.mapValues { visit(it.value) }
        return Object(ir.type, value)
    }

    override fun visit(ir: RhovasIr.Expression.Literal.Type): Object {
        //TODO(#11): Lookup the runtime type to account for anonymous types
        return Object(ir.type, ir.literal)
    }

    override fun visit(ir: RhovasIr.Expression.Group): Object {
        return visit(ir.expression)
    }

    override fun visit(ir: RhovasIr.Expression.Unary): Object {
        val expression = visit(ir.expression)
        val method = expression[ir.method] ?: throw error(
            ir,
            "Undefined method.",
            "The method op${ir.operator}() is not defined in ${expression.type.base.name}.",
        )
        return trace("${expression.type.base.name}.${ir.operator}()", ir.context.firstOrNull()) {
            method.invoke(listOf())
        }
    }

    override fun visit(ir: RhovasIr.Expression.Binary): Object {
        val left = visit(ir.left)
        return when (ir.operator) {
            "||" -> Object(Type.BOOLEAN, left.value as Boolean || visit(ir.right).value as Boolean)
            "&&" -> Object(Type.BOOLEAN, left.value as Boolean && visit(ir.right).value as Boolean)
            "==", "!=" -> {
                val method = left[ir.method!!] ?: throw error(
                    ir,
                    "Undefined method.",
                    "The method op==(${ir.method.parameters.map { it.type }}) is not defined in ${left.type.base.name}.",
                )
                val right = visit(ir.right)
                val result = if (right.type.isSubtypeOf(method.parameters[0].type)) {
                    trace("${left.type.base.name}.${method.name}(${method.parameters.map { it.type }.joinToString(", ")})", ir.context.firstOrNull()) {
                        method.invoke(listOf(right)).value as Boolean
                    }
                } else false
                val value = if (ir.operator == "==") result else !result
                Object(Type.BOOLEAN, value)
            }
            "===", "!==" -> {
                val right = visit(ir.right)
                val result = left.type == right.type && when (left.type.base.name) {
                    in listOf("Integer", "Decimal", "Atom") -> left.value == right.value
                    else -> left.value === right.value
                }
                val value = if (ir.operator == "===") result else !result
                Object(Type.BOOLEAN, value)
            }
            "<", ">", "<=", ">=" -> {
                val method = left[ir.method!!] ?: throw error(
                    ir,
                    "Undefined method.",
                    "The method ${ir.method.name}(${ir.method.parameters.map { it.type }.joinToString(", ")}) is not defined in ${left.type.base.name}.",
                )
                val right = visit(ir.right)
                require(right.type.isSubtypeOf(method.parameters[0].type)) { error(
                    ir.right,
                    "Invalid method argument type.",
                    "The method ${left.type.base.name}.${method.name}(${method.parameters.map { it.type }.joinToString(", ")}) requires argument 0 to be type ${method.parameters[0].type}, but received ${right.type}."
                ) }
                val result = trace("${left.type.base.name}.${method.name}(${method.parameters.map { it.type }.joinToString(", ")})", ir.context.firstOrNull()) {
                    method.invoke(listOf(right)).value as BigInteger
                }
                val value = when (ir.operator) {
                    "<" -> result < BigInteger.ZERO
                    ">" -> result > BigInteger.ZERO
                    "<=" -> result <= BigInteger.ZERO
                    ">=" -> result >= BigInteger.ZERO
                    else -> throw AssertionError()
                }
                Object(Type.BOOLEAN, value)
            }
            "+", "-", "*", "/" -> {
                val method = left[ir.method!!] ?: throw error(
                    ir,
                    "Undefined method.",
                    "The method ${ir.method.name}(${ir.method.parameters.map { it.type }.joinToString(", ")}) is not defined in ${left.type.base.name}.",
                )
                val right = visit(ir.right)
                trace("${left.type.base.name}.${method.name}(${method.parameters.map { it.type }.joinToString(", ")})", ir.context.firstOrNull()) {
                    method.invoke(listOf(right))
                }
            }
            else -> throw AssertionError()
        }
    }

    override fun visit(ir: RhovasIr.Expression.Access.Variable): Object {
        val variable = ir.variable as? Variable.Definition ?: scope.variables[ir.variable.name]!!
        return variable.value
    }

    override fun visit(ir: RhovasIr.Expression.Access.Property): Object {
        val receiver = visit(ir.receiver)
        return if (ir.coalesce && receiver.type.isSubtypeOf(Type.NULL)) {
            receiver
        } else {
            val method = receiver[ir.property]?.getter ?: throw error(
                ir,
                "Undefined property.",
                "The property ${ir.property.name} is not defined in ${receiver.type.base.name}.",
            )
            trace("${receiver.type.base.name}.${method.name}(${method.parameters.map { it.type }.joinToString(", ")})", ir.context.firstOrNull()) {
                method.invoke(listOf())
            }
        }
    }

    override fun visit(ir: RhovasIr.Expression.Access.Index): Object {
        val receiver = visit(ir.receiver)
        val arguments = ir.arguments.map { visit(it) }
        val method = receiver[ir.method] ?: throw error(
            ir,
            "Undefined method.",
            "The method ${ir.method.name}(${ir.method.parameters.map { it.type }.joinToString(", ")}) is not defined in ${receiver.type.base.name}.",
        )
        for (i in arguments.indices) {
            require(arguments[i].type.isSubtypeOf(method.parameters[i].type)) { error(
                ir.arguments[i],
                "Invalid method argument type.",
                "The method ${receiver.type.base.name}.${method.name}(${method.parameters.map { it.type }.joinToString(", ")}) requires argument ${i} to be type ${method.parameters[i].type}, but received ${arguments[i].type}.",
            ) }
        }
        return trace("${receiver.type.base.name}.${method.name}(${method.parameters.map { it.type }.joinToString(", ")})", ir.context.firstOrNull()) {
            method.invoke(arguments)
        }
    }

    override fun visit(ir: RhovasIr.Expression.Invoke.Constructor): Object {
        val function = ir.function as? Function.Definition ?: scope.types[ir.type.base.name]!!.functions[ir.function.name, ir.function.parameters.map { it.type }]!! as Function.Definition
        val arguments = ir.arguments.map { visit(it) }
        for (i in arguments.indices) {
            require(arguments[i].type.isSubtypeOf(ir.function.parameters[i].type)) { error(
                ir.arguments[i],
                "Invalid function argument type.",
                "The function ${ir.function.name}(${ir.function.parameters.map { it.type }.joinToString(", ")}) requires argument ${i} to be type ${ir.function.parameters[i].type}, but received ${arguments[i].type}.",
            ) }
        }
        return trace("${ir.type.base.name}(${ir.function.parameters.map { it.type }.joinToString(", ")})", ir.context.firstOrNull()) {
            function.invoke(arguments)
        }
    }

    override fun visit(ir: RhovasIr.Expression.Invoke.Function): Object {
        val function = ir.function as? Function.Definition ?: scope.functions[ir.function.name, ir.function.parameters.map { it.type }]!!
        val arguments = ir.arguments.map { visit(it) }
        for (i in arguments.indices) {
            require(arguments[i].type.isSubtypeOf(ir.function.parameters[i].type)) { error(
                ir.arguments[i],
                "Invalid function argument type.",
                "The function ${ir.function.name}(${ir.function.parameters.map { it.type }.joinToString(", ")}) requires argument ${i} to be type ${ir.function.parameters[i].type}, but received ${arguments[i].type}.",
            ) }
        }
        return trace("Source.${ir.function.name}(${ir.function.parameters.map { it.type }.joinToString(", ")})", ir.context.firstOrNull()) {
            function.invoke(arguments)
        }
    }

    override fun visit(ir: RhovasIr.Expression.Invoke.Method): Object {
        val receiver = visit(ir.receiver)
        return if (ir.coalesce && receiver.type.isSubtypeOf(Type.NULL)) {
            Object(Type.NULL, null)
        } else {
            val arguments = ir.arguments.map { visit(it) }
            val method = receiver[ir.method]  ?: throw error(
                ir,
                "Undefined method.",
                "The method ${ir.method.name}(${ir.method.parameters.map { it.type }.joinToString(", ")}) is not defined in ${receiver.type.base.name}.",
            )
            for (i in arguments.indices) {
                require(arguments[i].type.isSubtypeOf(method.parameters[i].type)) { error(
                    ir.arguments[i],
                    "Invalid method argument type.",
                    "The method ${receiver.type.base.name}.${method.name}(${method.parameters.map { it.type }.joinToString(", ")}) requires argument ${i} to be type ${method.parameters[i].type}, but received ${arguments[i].type}.",
                ) }
            }
            val result = trace("${receiver.type.base.name}.${method.name}(${method.parameters.map { it.type }.joinToString(", ")})", ir.context.firstOrNull()) {
                method.invoke(arguments)
            }
            return if (ir.cascade) receiver else result
        }
    }

    override fun visit(ir: RhovasIr.Expression.Invoke.Pipeline): Object {
        val function = when (ir.function) {
            is Function.Definition -> ir.function
            is Function.Declaration -> scope.functions[ir.function.name, ir.function.parameters.map { it.type }]!!
        }
        val receiver = visit(ir.receiver)
        return if (ir.coalesce && receiver.type.isSubtypeOf(Type.NULL)) {
            Object(Type.NULL, null)
        } else {
            val arguments = listOf(receiver) + ir.arguments.map { visit(it) }
            for (i in arguments.indices) {
                require(arguments[i].type.isSubtypeOf(ir.function.parameters[i].type)) { error(
                    ir.arguments[i],
                    "Invalid function argument type.",
                    "The function ${ir.qualifier?.let { "$it." } ?: ""}${ir.function.name}(${ir.function.parameters.map { it.type }.joinToString(", ")}) requires argument ${i} to be type ${ir.function.parameters[i].type}, but received ${arguments[i].type}.",
                ) }
            }
            val result = trace("Source.${ir.function.name}(${ir.function.parameters.map { it.type }.joinToString(", ")})", ir.context.firstOrNull()) {
                function.invoke(arguments)
            }
            return if (ir.cascade) receiver else result
        }
    }

    override fun visit(ir: RhovasIr.Expression.Lambda): Object {
        return Object(
            Type.LAMBDA[Type.DYNAMIC],
            Lambda(ir, scope, this)
        )
    }

    override fun visit(ir: RhovasIr.Pattern.Variable): Object {
        ir.variable?.let {
            val variable = Variable.Definition(it)
            variable.value = patternState.value
            patternState.scope.variables.define(variable)
        }
        return Object(Type.BOOLEAN, true)
    }

    override fun visit(ir: RhovasIr.Pattern.Value): Object {
        val value = visit(ir.value)
        //TODO(#2): Equatable<T> interface
        val method = value.methods["==", listOf(value.type)] ?: throw error(
            ir,
            "Undefined method.",
            "The method op==(${value.type}) is not defined in ${value.type.base.name}.",
        )
        val result = if (patternState.value.type.isSubtypeOf(method.parameters[0].type)) {
            trace("${value.type.base.name}.${method.name}(${method.parameters.map { it.type }.joinToString(", ")})", ir.context.firstOrNull()) {
                method.invoke(listOf(patternState.value)).value as Boolean
            }
        } else false
        return Object(Type.BOOLEAN, result)
    }

    override fun visit(ir: RhovasIr.Pattern.Predicate): Object {
        var result = visit(ir.pattern)
        if (result.value as Boolean) {
            //TODO(#15): Variable bindings
            result = visit(ir.predicate)
        }
        return result
    }

    override fun visit(ir: RhovasIr.Pattern.OrderedDestructure): Object {
        if (!patternState.value.type.isSubtypeOf(Type.LIST.ANY)) {
            return Object(Type.BOOLEAN, false)
        }
        val type = patternState.value.type.methods["get", listOf(Type.INTEGER)]!!.returns
        val list = patternState.value.value as List<Object>
        var i = 0
        for (pattern in ir.patterns) {
            val value = if (pattern is RhovasIr.Pattern.VarargDestructure) {
                val value = list.subList(i, list.size - ir.patterns.size + i + 1)
                i += value.size
                Object(Type.LIST[type], value)
            } else {
                list.getOrNull(i++) ?: return Object(Type.BOOLEAN, false)
            }
            patternState = patternState.copy(value = value)
            val result = visit(pattern)
            if (!(result.value as Boolean)) {
                return result
            }
        }
        if (i != list.size) {
            return Object(Type.BOOLEAN, false)
        }
        return Object(Type.BOOLEAN, true)
    }

    override fun visit(ir: RhovasIr.Pattern.NamedDestructure): Object {
        if (!patternState.value.type.isSubtypeOf(Type.OBJECT)) {
            return Object(Type.BOOLEAN, false)
        }
        val map = patternState.value.value as Map<String, Object>
        val named = ir.patterns.map { it.first }.toSet()
        var vararg = false
        for ((key, pattern) in ir.patterns) {
            val value = if (pattern is RhovasIr.Pattern.VarargDestructure) {
                vararg = true
                Object(Type.OBJECT, map.filterKeys { !named.contains(it) })
            } else {
                map[key] ?: return Object(Type.BOOLEAN, false)
            }
            if (key != null && (pattern as? RhovasIr.Pattern.Variable)?.variable?.name != key) {
                val variable = Variable.Definition(Variable.Declaration(key, value.type, false))
                variable.value = value
                patternState.scope.variables.define(variable)
            }
            patternState = patternState.copy(value = value)
            if (!(visit(pattern).value as Boolean)) {
                return Object(Type.BOOLEAN, false)
            }
        }
        if (!vararg && map.size != named.size) {
            return Object(Type.BOOLEAN, false)
        }
        return Object(Type.BOOLEAN, true)
    }

    override fun visit(ir: RhovasIr.Pattern.TypedDestructure): Object {
        if (!patternState.value.type.isSubtypeOf(ir.type)) {
            return Object(Type.BOOLEAN, false)
        }
        return ir.pattern?.let { visit(it) } ?: Object(Type.BOOLEAN, true)
    }

    override fun visit(ir: RhovasIr.Pattern.VarargDestructure): Object {
        if (patternState.value.type.isSubtypeOf(Type.LIST.ANY)) {
            val list = patternState.value.value as List<Object>
            if (ir.operator == "+" && list.isEmpty()) {
                return Object(Type.BOOLEAN, false)
            }
            return if (ir.pattern is RhovasIr.Pattern.Variable) {
                ir.pattern.variable?.let {
                    val variable = Variable.Definition(it)
                    variable.value = patternState.value
                    scope.variables.define(variable)
                }
                Object(Type.BOOLEAN, true)
            } else {
                //TODO(#15): Handle variable bindings
                Object(Type.BOOLEAN, list.all {
                    patternState = patternState.copy(value = it)
                    ir.pattern?.let { visit(it).value as Boolean } ?: true
                })
            }
        } else if (patternState.value.type.isSubtypeOf(Type.OBJECT)) {
            val map = patternState.value.value as Map<String, Object>
            if (ir.operator == "+" && map.isEmpty()) {
                return Object(Type.BOOLEAN, false)
            }
            return if (ir.pattern is RhovasIr.Pattern.Variable) {
                ir.pattern.variable?.let {
                    val variable = Variable.Definition(it)
                    variable.value = Object(Type.OBJECT, map)
                    scope.variables.define(variable)
                }
                Object(Type.BOOLEAN, true)
            } else {
                //TODO(#15): Handle variable bindings
                Object(Type.BOOLEAN, map.all {
                    patternState = patternState.copy(value = it.value)
                    ir.pattern?.let { visit(it).value as Boolean } ?: true
                })
            }
        } else {
            return Object(Type.BOOLEAN, false)
        }
    }

    override fun visit(ir: RhovasIr.Type): Object {
        throw AssertionError()
    }

    private fun <T> scoped(scope: Scope.Definition, block: () -> T): T {
        val original = this.scope
        this.scope = scope
        try {
            return block()
        } finally {
            this.scope = original
        }
    }

    private fun <T> trace(source: String, range: Input.Range?, block: () -> T): T {
        //TODO(#3): RhovasIr context
        stacktrace.addLast(stacktrace.removeLast().copy(range = range ?: Input.Range(0, 1, 0, 0)))
        stacktrace.addLast(StackFrame(source, Input.Range(0, 1, 0, 0)))
        try {
            return block()
        } finally {
            stacktrace.removeLast()
        }
    }

    fun require(condition: Boolean) {
        require(condition) { error(
            null,
            "Broken evaluator invariant.", """
                This is an internal compiler error, please report this!

                ${Exception().stackTraceToString()}
            """.trimIndent()
        ) }
    }

    fun require(condition: Boolean, error: () -> EvaluateException) {
        if (!condition) {
            throw error()
        }
    }

    fun error(ast: RhovasIr?, summary: String, details: String): EvaluateException {
        val range = ast?.context?.first() ?: Input.Range(0, 1, 0, 0)
        stacktrace.addLast(stacktrace.removeLast().copy(range = range))
        return EvaluateException(
            summary,
            details + "\n\n" + stacktrace.reversed().joinToString("\n") { " - ${it.source}, ${it.range.line}:${it.range.column}-${it.range.column + it.range.length}" },
            range,
            listOf(), //TODO(#3): Include context
        )
    }

    data class Break(val label: String?): Exception()

    data class Continue(val label: String?): Exception()

    data class Return(val ast: RhovasIr, val value: Object?): Exception()

    data class Throw(val exception: Object): Exception()

    data class Lambda(
        val ast: RhovasIr.Expression.Lambda,
        val scope: Scope.Definition,
        val evaluator: Evaluator,
    ) {

        fun invoke(arguments: List<Triple<String, Type, Object>>, returns: Type): Object {
            return evaluator.scoped(Scope.Definition(scope)) {
                if (ast.parameters.isNotEmpty()) {
                    for (i in ast.parameters.indices) {
                        val variable = Variable.Definition(ast.parameters[i])
                        variable.value = arguments[i].third
                        evaluator.scope.variables.define(variable)
                    }
                } else if (arguments.size == 1) {
                    val variable = Variable.Definition(Variable.Declaration("val", arguments[0].second, false))
                    variable.value = arguments[0].third
                    evaluator.scope.variables.define(variable)
                } else {
                    val variable = Variable.Definition(Variable.Declaration("val", Type.OBJECT, false) )
                    variable.value = Object(Type.OBJECT, arguments.associate { it.first to it.third })
                    evaluator.scope.variables.define(variable)
                }
                try {
                    evaluator.visit(ast.body)
                    Object(Type.VOID, Unit)
                } catch (e: Return) {
                    e.value ?: Object(Type.VOID, Unit)
                }
            }
        }

    }

    data class PatternState(
        val scope: Scope.Definition,
        val value: Object,
    )

}
