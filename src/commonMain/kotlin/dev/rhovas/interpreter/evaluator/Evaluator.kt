package dev.rhovas.interpreter.evaluator

import com.ionspin.kotlin.bignum.integer.BigInteger
import dev.rhovas.interpreter.analyzer.rhovas.RhovasIr
import dev.rhovas.interpreter.environment.*
import dev.rhovas.interpreter.environment.Function
import dev.rhovas.interpreter.parser.Input
import dev.rhovas.interpreter.parser.rhovas.RhovasAst

class Evaluator(private var scope: Scope.Definition) : RhovasIr.Visitor<Object> {

    private var label: String? = null
    private lateinit var patternState: PatternState
    private var stacktrace = ArrayDeque<StackFrame>().also {
        it.addLast(StackFrame("Source", Input.Range(0, 1, 0, 0)))
    }

    override fun visit(ir: RhovasIr.Source): Object {
        try {
            ir.statements.forEach { visit(it) }
        } catch (e: Throw) {
            throw error(ir,
                "Uncaught exception.",
                "An exception of type ${e.exception.type} was thrown: ${e.exception.methods.toString()}"
            )
        }
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
        val fields = ir.members.filterIsInstance<RhovasIr.Member.Property>().associateBy { it.getter.name }
        ir.type.base.scope.functions["", 1].first { it.parameters[0].type.isSubtypeOf(Type.STRUCT[Type.Struct(fields.filter { it.value.value == null }.mapValues { Variable.Declaration(it.key, it.value.getter.returns, false) })]) }.implementation = { arguments ->
            scoped(Scope.Definition(current)) {
                val type = Type.Reference(ir.type.base, ir.type.base.generics.map { Type.DYNAMIC })
                val initial = arguments[0].value as Map<String, Object>
                Object(type, fields.mapValues { initial[it.key] ?: it.value.value?.let { visit(it) } ?: Object(Type.NULLABLE.DYNAMIC, null) })
            }
        }
        ir.type.base.scope.functions["", fields.size].first { it.parameters.zip(fields.values).all { it.first.type.isSupertypeOf(it.second.getter.returns) } }.implementation = { arguments ->
            scoped(Scope.Definition(current)) {
                val type = Type.Reference(ir.type.base, ir.type.base.generics.map { Type.DYNAMIC })
                Object(type, fields.keys.withIndex().associate { it.value to arguments[it.index] })
            }
        }
        return Object(Type.VOID, Unit)
    }

    override fun visit(ir: RhovasIr.Component.Class): Object {
        //TODO(#11): Component declaration/definition handling
        if (!scope.types.isDefined(ir.type.base.name, true)) {
            scope.types.define(ir.type, ir.type.base.name)
        }
        ir.members.forEach { visit(it) }
        return Object(Type.VOID, Unit)
    }

    override fun visit(ir: RhovasIr.Component.Interface): Object {
        if (!scope.types.isDefined(ir.type.base.name, true)) {
            scope.types.define(ir.type, ir.type.base.name)
        }
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
                scope.variables.define(Variable.Definition(Variable.Declaration("this", ir.function.returns, false), Object(ir.function.returns, mutableMapOf<String, Object>())))
                for (i in ir.function.parameters.indices) {
                    scope.variables.define(Variable.Definition(ir.function.parameters[i], arguments[i]))
                }
                try {
                    visit(ir.block)
                } catch (e: Throw) {
                    require(ir.function.throws.any { e.exception.type.isSubtypeOf(it) }) { error(ir,
                        "Uncaught exception.",
                        "An exception of type ${e.exception.type} was thrown but not declared: ${e.exception.methods.toString()}"
                    ) }
                    throw e
                } catch (ignored: Return) {}
                scope.variables["this"]!!.value
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
        val variable = scope.variables["this"]!!
        val arguments = ir.arguments.map { visit(it) }
        ir.delegate?.let { variable.value = Object(variable.type, it.invoke(arguments).value) }
        ir.initializer?.let { (variable.value.value as MutableMap<String, Object>).putAll(visit(it).value as MutableMap<String, Object>) }
        return Object(Type.VOID, Unit)
    }

    override fun visit(ir: RhovasIr.Statement.Expression): Object {
        visit(ir.expression)
        return Object(Type.VOID, Unit)
    }

    override fun visit(ir: RhovasIr.Statement.Declaration.Variable): Object {
        val variable = ir.variable as? Variable.Definition ?: Variable.Definition(ir.variable as Variable.Declaration).also { scope.variables.define(it) }
        variable.value = ir.value?.let { visit(it) } ?: Object(Type.NULLABLE.DYNAMIC, null)
        return Object(Type.VOID, Unit)
    }

    override fun visit(ir: RhovasIr.Statement.Declaration.Function): Object {
        val current = scope
        val function = ir.function as? Function.Definition ?: Function.Definition(ir.function as Function.Declaration).also { scope.functions.define(it) }
        function.implementation = { arguments ->
            scoped(Scope.Definition(current)) {
                for (i in ir.function.parameters.indices) {
                    scope.variables.define(Variable.Definition(ir.function.parameters[i], arguments[i]))
                }
                try {
                    visit(ir.block)
                    Object(Type.VOID, Unit)
                } catch (e: Throw) {
                    require(ir.function.throws.any { e.exception.type.isSubtypeOf(it) }) { error(ir,
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
        val property = receiver[ir.property] ?: throw error(ir,
            "Undefined property.",
            "The property ${ir.property.name} is not defined in ${receiver.type.base.name}.",
        )
        val method = property.setter ?: throw error(ir,
            "Unassignable property.",
            "The property ${receiver.type.base.name}.${ir.property.name} does not support assignment.",
        )
        require(value.type.isSubtypeOf(method.parameters[0].type)) { error(ir.value,
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
        val method = receiver[ir.method]  ?: throw error(ir,
            "Undefined method.",
            "The method ${ir.method.name}(${ir.method.parameters.map { it.type }.joinToString(", ")}) is not defined in ${receiver.type.base.name}.",
        )
        for (i in arguments.indices) {
            require(arguments[i].type.isSubtypeOf(method.parameters[i].type)) { error(ir.arguments.getOrNull(i) ?: ir.value,
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
                require(it.first == null || visit(it.first!!).value as Boolean) { error(ir.elseCase.first,
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
                require(predicate.invoke(it.first ?: RhovasIr.Pattern.Variable(Variable.Declaration("_", argument.type, false)))) { error(ir.elseCase.first,
                    "Failed match else assertion.",
                    "A structural match statement requires the else pattern to match.",
                ) }
            }
            ?: throw error(ir,
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
        val iterator = iterable.methods["iterator", listOf()]!!.invoke(listOf())
        val next = iterator.methods["next", listOf()]!!
        val label = this.label
        this.label = null
        for (element in generateSequence { (next.invoke(listOf()).value as Pair<Object, Object>?)?.first }) {
            try {
                scoped(Scope.Definition(scope)) {
                    scope.variables.define(Variable.Definition(ir.variable, element))
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
                    scope.variables.define(Variable.Definition(catch.variable, e.exception))
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
            ir.variable?.let { scope.variables.define(Variable.Definition(ir.variable, argument)) }
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
        val value = ir.value?.let { visit(it) }
        scoped(Scope.Definition(scope)) {
            value?.let { scope.variables.define(Variable.Definition(Variable.Declaration("val", it.type, false), it)) }
            ir.ensures.forEach { visit(it) }
        }
        throw Return(ir, value)
    }

    override fun visit(ir: RhovasIr.Statement.Throw): Object {
        throw Throw(visit(ir.exception))
    }

    override fun visit(ir: RhovasIr.Statement.Assert): Object {
        val condition = visit(ir.condition)
        require(condition.value as Boolean) { error(ir,
            "Failed assertion",
            "The assertion failed" + ir.message?.let { " (${visit(it).value})" }.orEmpty() + ".",
        ) }
        return Object(Type.VOID, Unit)
    }

    override fun visit(ir: RhovasIr.Statement.Require): Object {
        val condition = visit(ir.condition)
        require(condition.value as Boolean) { error(ir,
            "Failed precondition assertion.",
            "The precondition assertion failed" + ir.message?.let { " (${visit(it).value})" }.orEmpty() + ".",
        ) }
        return Object(Type.VOID, Unit)
    }

    override fun visit(ir: RhovasIr.Statement.Ensure): Object {
        val condition = visit(ir.condition)
        require(condition.value as Boolean) { error(ir,
            "Failed postcondition assertion.",
            "The postcondition assertion failed" + ir.message?.let { " (${visit(it).value})" }.orEmpty() + ".",
        ) }
        return Object(Type.VOID, Unit)
    }

    override fun visit(ir: RhovasIr.Expression.Block): Object {
        val expression = scoped(Scope.Definition(scope)) {
            ir.statements.forEach { visit(it) }
            ir.expression?.let { visit(it) }
        }
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
        return if (ir.type.base == Type.TUPLE.GENERIC.base) {
            Object(Type.TUPLE.DYNAMIC, value)
        } else {
            Object(Type.LIST.DYNAMIC, value)
        }
    }

    override fun visit(ir: RhovasIr.Expression.Literal.Object): Object {
        return if (ir.type.base == Type.MAP.GENERIC.base) {
            val value = ir.properties.entries.associate { Object.Hashable(Object(Type.ATOM, RhovasAst.Atom(it.key))) to visit(it.value) }
            Object(Type.MAP[Type.ATOM, Type.DYNAMIC], value)
        } else {
            val value = ir.properties.mapValues { visit(it.value) }
            Object(Type.STRUCT.DYNAMIC, value)
        }
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
        val method = expression[ir.method] ?: throw error(ir,
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
                val equals = left.methods.equals(visit(ir.right))
                val value = if (ir.operator == "==") equals else !equals
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
                val method = left[ir.method!!] ?: throw error(ir,
                    "Undefined method.",
                    "The method ${ir.method.name}(${ir.method.parameters.map { it.type }.joinToString(", ")}) is not defined in ${left.type.base.name}.",
                )
                val right = visit(ir.right)
                require(right.type.isSubtypeOf(method.parameters[0].type)) { error(ir.right,
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
                val method = left[ir.method!!] ?: throw error(ir,
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
        val original = visit(ir.receiver)
        val receiver = computeCoalesceReceiver(original, ir.coalesce) ?: return original
        val method = receiver[ir.property]?.getter ?: throw error(ir,
            "Undefined property.",
            "The property ${ir.property.name} is not defined in ${receiver.type.base.name}.",
        )
        val returns = trace("${receiver.type.base.name}.${method.name}(${method.parameters.map { it.type }.joinToString(", ")})", ir.context.firstOrNull()) {
            invokeBang(ir.bang, method.throws) { method.invoke(listOf()) }
        }
        return computeCoalesceCascadeReturn(returns, original, ir.coalesce, false)
    }

    override fun visit(ir: RhovasIr.Expression.Access.Index): Object {
        val original = visit(ir.receiver)
        val receiver = computeCoalesceReceiver(original, ir.coalesce) ?: return original
        val arguments = ir.arguments.map { visit(it) }
        val method = receiver[ir.method] ?: throw error(ir,
            "Undefined method.",
            "The method ${ir.method.name}(${ir.method.parameters.map { it.type }.joinToString(", ")}) is not defined in ${receiver.type.base.name}.",
        )
        for (i in arguments.indices) {
            require(arguments[i].type.isSubtypeOf(method.parameters[i].type)) { error(ir.arguments[i],
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
            require(arguments[i].type.isSubtypeOf(ir.function.parameters[i].type)) { error(ir.arguments[i],
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
            require(arguments[i].type.isSubtypeOf(ir.function.parameters[i].type)) { error(ir.arguments[i],
                "Invalid function argument type.",
                "The function ${ir.function.name}(${ir.function.parameters.map { it.type }.joinToString(", ")}) requires argument ${i} to be type ${ir.function.parameters[i].type}, but received ${arguments[i].type}.",
            ) }
        }
        return trace("Source.${ir.function.name}(${ir.function.parameters.map { it.type }.joinToString(", ")})", ir.context.firstOrNull()) {
            invokeBang(ir.bang, function.throws) { function.invoke(arguments) }
        }
    }

    override fun visit(ir: RhovasIr.Expression.Invoke.Method): Object {
        val original = visit(ir.receiver)
        val receiver = computeCoalesceReceiver(original, ir.coalesce) ?: return original
        val arguments = ir.arguments.map { visit(it) }
        val method = receiver[ir.method]  ?: throw error(ir,
            "Undefined method.",
            "The method ${ir.method.name}(${ir.method.parameters.map { it.type }.joinToString(", ")}) is not defined in ${receiver.type.base.name}.",
        )
        for (i in arguments.indices) {
            require(arguments[i].type.isSubtypeOf(method.parameters[i].type)) { error(ir.arguments[i],
                "Invalid method argument type.",
                "The method ${receiver.type.base.name}.${method.name}(${method.parameters.map { it.type }.joinToString(", ")}) requires argument ${i} to be type ${method.parameters[i].type}, but received ${arguments[i].type}.",
            ) }
        }
        val returns = trace("${receiver.type.base.name}.${method.name}(${method.parameters.map { it.type }.joinToString(", ")})", ir.context.firstOrNull()) {
            invokeBang(ir.bang, method.throws) { method.invoke(arguments) }
        }
        return computeCoalesceCascadeReturn(returns, original, ir.coalesce, ir.cascade)
    }

    override fun visit(ir: RhovasIr.Expression.Invoke.Pipeline): Object {
        val function = ir.function as? Function.Definition ?: scope.functions[ir.function.name, ir.function.parameters.map { it.type }]!!
        val original = visit(ir.receiver)
        val receiver = computeCoalesceReceiver(original, ir.coalesce) ?: return original
        val arguments = listOf(receiver) + ir.arguments.map { visit(it) }
        for (i in arguments.indices) {
            require(arguments[i].type.isSubtypeOf(ir.function.parameters[i].type)) { error(ir.arguments[i],
                "Invalid function argument type.",
                "The function ${ir.qualifier?.let { "$it." } ?: ""}${ir.function.name}(${ir.function.parameters.map { it.type }.joinToString(", ")}) requires argument ${i} to be type ${ir.function.parameters[i].type}, but received ${arguments[i].type}.",
            ) }
        }
        val returns = trace("Source.${ir.function.name}(${ir.function.parameters.map { it.type }.joinToString(", ")})", ir.context.firstOrNull()) {
            invokeBang(ir.bang, function.throws) { function.invoke(arguments) }
        }
        return computeCoalesceCascadeReturn(returns, original, ir.coalesce, ir.cascade)
    }

    private fun computeCoalesceReceiver(receiver: Object, coalesce: Boolean): Object? {
        return when {
            coalesce -> (receiver.value as Pair<Object?, Object?>?)?.first
            else -> receiver
        }
    }

    private fun computeCoalesceCascadeReturn(returns: Object, receiver: Object, coalesce: Boolean, cascade: Boolean): Object {
        return when {
            cascade -> receiver
            coalesce -> Object(receiver.type, Pair(returns, null))
            else -> returns
        }
    }

    override fun visit(ir: RhovasIr.Expression.Lambda): Object {
        return Object(Type.LAMBDA.DYNAMIC, Lambda(ir, scope, this))
    }

    override fun visit(ir: RhovasIr.Pattern.Variable): Object {
        ir.variable?.let { patternState.scope.variables.define(Variable.Definition(it, patternState.value)) }
        return Object(Type.BOOLEAN, true)
    }

    override fun visit(ir: RhovasIr.Pattern.Value): Object {
        val value = visit(ir.value)
        val result = value.methods.equals(patternState.value)
        return Object(Type.BOOLEAN, result)
    }

    override fun visit(ir: RhovasIr.Pattern.Predicate): Object {
        var result = visit(ir.pattern)
        if (result.value as Boolean) {
            result = scoped(Scope.Definition(scope)) {
                scope.variables.define(Variable.Definition(Variable.Declaration("val", patternState.value.type, false), patternState.value))
                ir.pattern.bindings.forEach { scope.variables.define(Variable.Definition(it.value, patternState.scope.variables[it.key]!!.value)) }
                visit(ir.predicate)
            }
        }
        return result
    }

    override fun visit(ir: RhovasIr.Pattern.OrderedDestructure): Object {
        if (!patternState.value.type.isSubtypeOf(Type.LIST.DYNAMIC)) {
            return Object(Type.BOOLEAN, false)
        }
        val type = patternState.value.type.generic("T", Type.LIST.GENERIC)!!
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
        if (!patternState.value.type.isSubtypeOf(Type.STRUCT.GENERIC)) {
            return Object(Type.BOOLEAN, false)
        }
        val map = patternState.value.value as Map<String, Object>
        val named = ir.patterns.map { it.first }.toSet()
        var vararg = false
        for ((key, pattern) in ir.patterns) {
            val value = if (pattern is RhovasIr.Pattern.VarargDestructure) {
                vararg = true
                Object(Type.STRUCT.DYNAMIC, map.filterKeys { !named.contains(it) })
            } else {
                map[key] ?: return Object(Type.BOOLEAN, false)
            }
            if (key != null && (pattern as? RhovasIr.Pattern.Variable)?.variable?.name != key) {
                patternState.scope.variables.define(Variable.Definition(Variable.Declaration(key, value.type, false), value))
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
        if (patternState.value.type.isSubtypeOf(Type.LIST.DYNAMIC)) {
            val list = patternState.value.value as List<Object>
            if (ir.operator == "+" && list.isEmpty()) {
                return Object(Type.BOOLEAN, false)
            }
            return if (ir.pattern is RhovasIr.Pattern.Variable) {
                ir.pattern.variable?.let { patternState.scope.variables.define(Variable.Definition(it, patternState.value)) }
                Object(Type.BOOLEAN, true)
            } else {
                val bindings = ir.bindings.mapValues { Variable.Definition(it.value, Object(Type.LIST.DYNAMIC, mutableListOf<Object>())) }
                val parent = patternState
                val result = list.all {
                    patternState = PatternState(Scope.Definition(parent.scope), it)
                    if (ir.pattern?.let { visit(it).value as Boolean } != false) {
                        bindings.forEach { (it.value.value.value as MutableList<Object>).add(patternState.scope.variables[it.key]!!.value) }
                        true
                    } else false
                }
                patternState = parent
                bindings.forEach { patternState.scope.variables.define(it.value) }
                Object(Type.BOOLEAN, result)
            }
        } else if (patternState.value.type.isSubtypeOf(Type.STRUCT.DYNAMIC)) {
            val map = patternState.value.value as Map<String, Object>
            if (ir.operator == "+" && map.isEmpty()) {
                return Object(Type.BOOLEAN, false)
            }
            return if (ir.pattern is RhovasIr.Pattern.Variable) {
                ir.pattern.variable?.let { patternState.scope.variables.define(Variable.Definition(it, Object(Type.STRUCT.DYNAMIC, map))) }
                Object(Type.BOOLEAN, true)
            } else {
                val bindings = ir.bindings.mapValues { Variable.Definition(it.value, Object(Type.STRUCT.DYNAMIC, mutableMapOf<String, Object>())) }
                val parent = patternState
                val result = map.all { entry ->
                    patternState = PatternState(Scope.Definition(parent.scope), entry.value)
                    if (ir.pattern?.let { visit(it).value as Boolean } != false) {
                        bindings.forEach { (it.value.value.value as MutableMap<String, Object>)[entry.key] = (patternState.scope.variables[it.key]!!.value) }
                        true
                    } else false
                }
                patternState = parent
                bindings.forEach { patternState.scope.variables.define(it.value) }
                Object(Type.BOOLEAN, result)
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

    private fun invokeBang(bang: Boolean, throws: List<Type>, invoke: () -> Object): Object {
        return when {
            bang && throws.isEmpty() -> invoke().methods["value!", listOf()]!!.invoke(listOf())
            !bang && throws.isNotEmpty() -> {
                try {
                    val result = invoke()
                    Object(Type.RESULT[result.type, Type.DYNAMIC], Pair(result, null))
                } catch (e: Throw) {
                    when {
                        throws.any { it.isSupertypeOf(e.exception.type) } -> Object(Type.RESULT[Type.DYNAMIC, e.exception.type], Pair(null, e.exception))
                        else -> throw e
                    }
                }
            }
            else -> invoke()
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
        require(condition) { error(null,
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
        val ir: RhovasIr.Expression.Lambda,
        val scope: Scope.Definition,
        val evaluator: Evaluator,
    ) {

        fun invoke(arguments: List<Object>, returns: Type): Object {
            return evaluator.scoped(Scope.Definition(scope)) {
                when {
                    ir.parameters.isNotEmpty() -> {
                        evaluator.require(arguments.size == ir.parameters.size) { evaluator.error(ir,
                            "Invalid lambda argument count.",
                            "The invoked lambda defined ${ir.parameters.size} parameter(s), but received ${arguments.size} argument(s).",
                        )}
                        arguments.indices.forEach {
                            evaluator.require(arguments[it].type.isSubtypeOf(ir.parameters[it].type)) { evaluator.error(ir,
                                "Invalid lambda argument type.",
                                "The invoked lambda requires argument ${it} to be type ${ir.parameters[it].type}, but received ${arguments[it].type}.",
                            ) }
                            evaluator.scope.variables.define(Variable.Definition(ir.parameters[it], arguments[it]))
                        }
                    }
                    arguments.isEmpty() -> evaluator.scope.variables.define(Variable.Definition(Variable.Declaration("val", Type.VOID, false), Object(Type.VOID, Unit)))
                    arguments.size == 1 -> evaluator.scope.variables.define(Variable.Definition(Variable.Declaration("val", arguments[0].type, false), arguments[0]))
                    else -> {
                        val type = Type.TUPLE[arguments.map { it.type }]
                        evaluator.scope.variables.define(Variable.Definition(Variable.Declaration("val", type, false), Object(type, arguments)))
                    }
                }
                val result = try {
                    evaluator.visit(ir.body)
                } catch (e: Return) {
                    e.value ?: Object(Type.VOID, Unit)
                }
                evaluator.require(result.type.isSupertypeOf(returns)) { evaluator.error(ir,
                    "Invalid lambda return value.",
                    "The invoked lambda requires the return value to be type ${returns}, but received ${result.type}."
                ) }
                result
            }
        }

    }

    data class PatternState(
        val scope: Scope.Definition,
        val value: Object,
    )

}
