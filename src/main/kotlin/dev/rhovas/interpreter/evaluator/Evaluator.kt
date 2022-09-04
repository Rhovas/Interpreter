package dev.rhovas.interpreter.evaluator

import dev.rhovas.interpreter.analyzer.rhovas.RhovasIr
import dev.rhovas.interpreter.environment.*
import dev.rhovas.interpreter.environment.Function
import dev.rhovas.interpreter.library.Library
import dev.rhovas.interpreter.parser.Input
import java.math.BigInteger

class Evaluator(private var scope: Scope.Definition) : RhovasIr.Visitor<Object> {

    private var label: String? = null
    private lateinit var patternState: PatternState
    private var stacktrace = ArrayDeque<StackFrame>().also {
        it.addLast(StackFrame("Source", Input.Range(0, 1, 0, 0)))
    }

    override fun visit(ir: RhovasIr.Source): Object {
        ir.statements.forEach { visit(it) }
        return Object(Library.TYPES["Void"]!!, Unit)
    }

    override fun visit(ir: RhovasIr.Import): Object {
        throw AssertionError()
    }

    override fun visit(ir: RhovasIr.Component.Struct): Object {
        scope.types.define(ir.type, ir.type.base.name)
        val current = scope
        //TODO: Hack to access unwrapped function definition
        val constructor = ir.type.base.scope.functions[ir.type.base.name, 1].single()
        constructor.implementation = { arguments ->
            scoped(current) {
                Object(ir.type, ir.fields.associate {
                    val value = (arguments[0].value as Map<String, Object>)[it.variable.name]
                        ?: it.value?.let { visit(it) }
                        ?: Object(Library.TYPES["Null"]!!, null)
                    Pair(it.variable.name, value)
                }.toMutableMap())
            }
        }
        scope.functions.define(constructor)
        return Object(Library.TYPES["Void"]!!, Unit)
    }

    override fun visit(ir: RhovasIr.Statement.Block): Object {
        scoped(Scope.Definition(scope)) {
            ir.statements.forEach { visit(it) }
        }
        return Object(Library.TYPES["Void"]!!, Unit)
    }

    override fun visit(ir: RhovasIr.Statement.Component): Object {
        visit(ir.component)
        return Object(Library.TYPES["Void"]!!, Unit)
    }

    override fun visit(ir: RhovasIr.Statement.Expression): Object {
        visit(ir.expression)
        return Object(Library.TYPES["Void"]!!, Unit)
    }

    override fun visit(ir: RhovasIr.Statement.Function): Object {
        val current = scope
        val definition = when (ir.function) {
            is Function.Definition -> ir.function
            is Function.Declaration -> Function.Definition(ir.function)
        }
        definition.implementation = { arguments ->
            scoped(current) {
                for (i in ir.function.parameters.indices) {
                    val parameter = Variable.Definition(Variable.Declaration(ir.function.parameters[i].first, ir.function.parameters[i].second, false))
                    parameter.value = arguments[i]
                    scope.variables.define(parameter)
                }
                try {
                    visit(ir.body)
                    Object(Library.TYPES["Void"]!!, Unit)
                } catch (e: Throw) {
                    require(ir.function.throws.any { e.exception.type.isSubtypeOf(it) }) { error(
                        ir,
                        "Uncaught exception.",
                        "An exception of type ${e.exception.type} was thrown but not declared: ${e.exception.methods["toString", listOf()]!!.invoke(listOf()).value as String}"
                    ) }
                    throw e
                } catch (e: Return) {
                    e.value ?: Object(Library.TYPES["Void"]!!, Unit)
                }
            }
        }
        scope.functions.define(definition)
        return Object(Library.TYPES["Void"]!!, Unit)
    }

    override fun visit(ir: RhovasIr.Statement.Declaration): Object {
        val definition = when (ir.variable) {
            is Variable.Definition -> ir.variable
            is Variable.Declaration -> Variable.Definition(ir.variable)
        }
        definition.value = ir.value?.let { visit(it) } ?: Object(Library.TYPES["Null"]!!, null)
        scope.variables.define(definition)
        return Object(Library.TYPES["Void"]!!, Unit)
    }

    override fun visit(ir: RhovasIr.Statement.Assignment.Variable): Object {
        val variable = scope.variables[ir.variable.name]!!
        require(variable.mutable) { error(
            ir,
            "Unassignable variable.",
            "The variable ${variable.name} is immutable and does not support assignment.",
        ) }
        variable.value = visit(ir.value)
        return Object(Library.TYPES["Void"]!!, Unit)
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
        require(value.type.isSubtypeOf(method.parameters[0].second)) { error(
            ir.value,
            "Invalid property value type.",
            "The property ${receiver.type.base.name}.${method.name} requires the value to be type ${method.parameters[0].second}, but received ${value.type}.",
        ) }
        trace("${receiver.type.base.name}.${method.name}(${method.parameters.map { it.second }.joinToString(", ")})", ir.context.firstOrNull()) {
            method.invoke(listOf(value))
        }
        return Object(Library.TYPES["Void"]!!, Unit)
    }

    override fun visit(ir: RhovasIr.Statement.Assignment.Index): Object {
        val receiver = visit(ir.receiver)
        val arguments = ir.arguments.map { visit(it) } + listOf(visit(ir.value))
        val method = receiver[ir.method]  ?: throw error(
            ir,
            "Undefined method.",
            "The method ${ir.method.name}(${ir.method.parameters.map { it.second }.joinToString(", ")}) is not defined in ${receiver.type.base.name}.",
        )
        for (i in arguments.indices) {
            require(arguments[i].type.isSubtypeOf(method.parameters[i].second)) { error(
                ir.arguments.getOrNull(i) ?: ir.value,
                "Invalid method argument type.",
                "The method ${receiver.type.base.name}.${method.name}(${method.parameters.map { it.second }.joinToString(", ")}) requires argument ${i} to be type ${method.parameters[i].second}, but received ${arguments[i].type}.",
            ) }
        }
        trace("${receiver.type.base.name}.${method.name}(${method.parameters.map { it.second }.joinToString(", ")})", ir.context.firstOrNull()) {
            method.invoke(arguments)
        }
        return Object(Library.TYPES["Void"]!!, Unit)
    }

    override fun visit(ir: RhovasIr.Statement.If): Object {
        val condition = visit(ir.condition)
        if (condition.value as Boolean) {
            visit(ir.thenStatement)
        } else if (ir.elseStatement != null) {
            visit(ir.elseStatement)
        }
        return Object(Library.TYPES["Void"]!!, Unit)
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
        return Object(Library.TYPES["Void"]!!, Unit)
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
                "A structural match statements requires the patterns to be exhaustive, but no pattern matched argument ${argument.methods["toString", listOf()]!!.invoke(listOf()).value as String}.",
            )
        scoped(patternState.scope) {
            visit(case.second)
        }
        return Object(Library.TYPES["Void"]!!, Unit)
    }

    override fun visit(ir: RhovasIr.Statement.For): Object {
        val iterable = visit(ir.iterable)
        val label = this.label
        this.label = null
        for (element in iterable.value as List<Object>) {
            try {
                scoped(Scope.Definition(scope)) {
                    //TODO: Generic types
                    val variable = Variable.Definition(Variable.Declaration(ir.name, Library.TYPES["Any"]!!, false))
                    variable.value = element
                    scope.variables.define(variable)
                    visit(ir.body)
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
        return Object(Library.TYPES["Void"]!!, Unit)
    }

    override fun visit(ir: RhovasIr.Statement.While): Object {
        val label = this.label
        this.label = null
        while (true) {
            val condition = visit(ir.condition)
            if (condition.value as Boolean) {
                try {
                    scoped(Scope.Definition(scope)) {
                        visit(ir.body)
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
        return Object(Library.TYPES["Void"]!!, Unit)
    }

    override fun visit(ir: RhovasIr.Statement.Try): Object {
        try {
            visit(ir.body)
        } catch (e: Throw) {
            val catch = ir.catches.firstOrNull { e.exception.type.isSubtypeOf(it.type) }
            //TODO: Nested exceptions
            if (catch != null) {
                scoped(Scope.Definition(scope)) {
                    val variable = Variable.Definition(Variable.Declaration(catch.name, catch.type, false))
                    variable.value = e.exception
                    scope.variables.define(variable)
                    visit(catch.body)
                }
            } else {
                ir.finallyStatement?.let { visit(it) }
                throw e
            }
        }
        ir.finallyStatement?.let { visit(it) }
        return Object(Library.TYPES["Void"]!!, Unit)
    }

    override fun visit(ir: RhovasIr.Statement.Try.Catch): Object {
        throw AssertionError()
    }

    override fun visit(ir: RhovasIr.Statement.With): Object {
        TODO()
    }

    override fun visit(ir: RhovasIr.Statement.Label): Object {
        val label = label
        this.label = ir.label
        try {
            visit(ir.statement)
        } finally {
            this.label = label
        }
        return Object(Library.TYPES["Void"]!!, Unit)
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
            "The assertion failed" + ir.message?.let { " (${visit(it).value})" } + ".",
        ) }
        return Object(Library.TYPES["Void"]!!, Unit)
    }

    override fun visit(ir: RhovasIr.Statement.Require): Object {
        val condition = visit(ir.condition)
        require(condition.value as Boolean) { error(
            ir,
            "Failed precondition assertion.",
            "The precondition assertion failed" + ir.message?.let { " (${visit(it).value})" } + ".",
        ) }
        return Object(Library.TYPES["Void"]!!, Unit)
    }

    override fun visit(ir: RhovasIr.Statement.Ensure): Object {
        val condition = visit(ir.condition)
        require(condition.value as Boolean) { error(
            ir,
            "Failed postcondition assertion.",
            "The postcondition assertion failed" + ir.message?.let { " (${visit(it).value})" } + ".",
        ) }
        return Object(Library.TYPES["Void"]!!, Unit)
    }

    override fun visit(ir: RhovasIr.Expression.Literal.Scalar): Object {
        return Object(ir.type, ir.value)
    }

    override fun visit(ir: RhovasIr.Expression.Literal.String): Object {
        val builder = StringBuilder()
        ir.arguments.indices.forEach {
            builder.append(ir.literals[it])
            builder.append(visit(ir.arguments[it]).methods["toString", listOf()]!!.invoke(listOf()).value as String)
        }
        builder.append(ir.literals.last())
        return Object(Library.TYPES["String"]!!, builder.toString())
    }

    override fun visit(ir: RhovasIr.Expression.Literal.List): Object {
        val value = ir.elements.map { visit(it) }
        return Object(ir.type, value)
    }

    override fun visit(ir: RhovasIr.Expression.Literal.Object): Object {
        val value = ir.properties.mapValues { visit(it.value) }
        return Object(ir.type, value)
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
            "||" -> Object(Library.TYPES["Boolean"]!!, left.value as Boolean || visit(ir.right).value as Boolean)
            "&&" -> Object(Library.TYPES["Boolean"]!!, left.value as Boolean && visit(ir.right).value as Boolean)
            "==", "!=" -> {
                val method = left[ir.method!!] ?: throw error(
                    ir,
                    "Undefined method.",
                    "The method op==(${ir.method.parameters.map { it.second }}) is not defined in ${left.type.base.name}.",
                )
                val right = visit(ir.right)
                val result = if (right.type.isSubtypeOf(method.parameters[0].second)) {
                    trace("${left.type.base.name}.${method.name}(${method.parameters.map { it.second }.joinToString(", ")})", ir.context.firstOrNull()) {
                        method.invoke(listOf(right)).value as Boolean
                    }
                } else false
                val value = if (ir.operator == "==") result else !result
                Object(Library.TYPES["Boolean"]!!, value)
            }
            "===", "!==" -> {
                val right = visit(ir.right)
                //TODO: Implementation non-primitives (Integer/Decimal/String/Atom)
                val result = if (left.type == right.type) left.value === right.value else false
                val value = if (ir.operator == "===") result else !result
                Object(Library.TYPES["Boolean"]!!, value)
            }
            "<", ">", "<=", ">=" -> {
                val method = left[ir.method!!] ?: throw error(
                    ir,
                    "Undefined method.",
                    "The method ${ir.method.name}(${ir.method.parameters.map { it.second }.joinToString(", ")}) is not defined in ${left.type.base.name}.",
                )
                val right = visit(ir.right)
                require(right.type.isSubtypeOf(method.parameters[0].second)) { error(
                    ir.right,
                    "Invalid method argument type.",
                    "The method ${left.type.base.name}.${method.name}(${method.parameters.map { it.second }.joinToString(", ")}) requires argument 0 to be type ${method.parameters[0].second}, but received ${right.type}."
                ) }
                val result = trace("${left.type.base.name}.${method.name}(${method.parameters.map { it.second }.joinToString(", ")})", ir.context.firstOrNull()) {
                    method.invoke(listOf(right)).value as BigInteger
                }
                val value = when (ir.operator) {
                    "<" -> result < BigInteger.ZERO
                    ">" -> result > BigInteger.ZERO
                    "<=" -> result <= BigInteger.ZERO
                    ">=" -> result >= BigInteger.ZERO
                    else -> throw AssertionError()
                }
                Object(Library.TYPES["Boolean"]!!, value)
            }
            "+", "-", "*", "/" -> {
                val method = left[ir.method!!] ?: throw error(
                    ir,
                    "Undefined method.",
                    "The method ${ir.method.name}(${ir.method.parameters.map { it.second }.joinToString(", ")}) is not defined in ${left.type.base.name}.",
                )
                val right = visit(ir.right)
                trace("${left.type.base.name}.${method.name}(${method.parameters.map { it.second }.joinToString(", ")})", ir.context.firstOrNull()) {
                    method.invoke(listOf(right))
                }
            }
            else -> throw AssertionError()
        }
    }

    override fun visit(ir: RhovasIr.Expression.Access.Variable): Object {
        val variable = scope.variables[ir.variable.name]!!
        return variable.value
    }

    override fun visit(ir: RhovasIr.Expression.Access.Property): Object {
        val receiver = visit(ir.receiver)
        return if (ir.coalesce && receiver.type.isSubtypeOf(Library.TYPES["Null"]!!)) {
            receiver
        } else {
            val method = receiver[ir.property]?.getter ?: throw error(
                ir,
                "Undefined property.",
                "The property ${ir.property.name} is not defined in ${receiver.type.base.name}.",
            )
            trace("${receiver.type.base.name}.${method.name}(${method.parameters.map { it.second }.joinToString(", ")})", ir.context.firstOrNull()) {
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
            "The method ${ir.method.name}(${ir.method.parameters.map { it.second }.joinToString(", ")}) is not defined in ${receiver.type.base.name}.",
        )
        for (i in arguments.indices) {
            require(arguments[i].type.isSubtypeOf(method.parameters[i].second)) { error(
                ir.arguments[i],
                "Invalid method argument type.",
                "The method ${receiver.type.base.name}.${method.name}(${method.parameters.map { it.second }.joinToString(", ")}) requires argument ${i} to be type ${method.parameters[i].second}, but received ${arguments[i].type}.",
            ) }
        }
        return trace("${receiver.type.base.name}.${method.name}(${method.parameters.map { it.second }.joinToString(", ")})", ir.context.firstOrNull()) {
            method.invoke(arguments)
        }
    }

    override fun visit(ir: RhovasIr.Expression.Invoke.Function): Object {
        val function = scope.functions[ir.function.name, ir.function.parameters.map { it.second }]!!
        val arguments = ir.arguments.map { visit(it) }
        for (i in arguments.indices) {
            require(arguments[i].type.isSubtypeOf(ir.function.parameters[i].second)) { error(
                ir.arguments[i],
                "Invalid function argument type.",
                "The function ${ir.function.name}(${ir.function.parameters.map { it.second }.joinToString(", ")}) requires argument ${i} to be type ${ir.function.parameters[i].second}, but received ${arguments[i].type}.",
            ) }
        }
        return trace("Source.${ir.function.name}(${ir.function.parameters.map { it.second }.joinToString(", ")})", ir.context.firstOrNull()) {
            function.invoke(arguments)
        }
    }

    override fun visit(ir: RhovasIr.Expression.Invoke.Method): Object {
        val receiver = visit(ir.receiver)
        return if (ir.coalesce && receiver.type.isSubtypeOf(Library.TYPES["Null"]!!)) {
            Object(Library.TYPES["Null"]!!, null)
        } else {
            val arguments = ir.arguments.map { visit(it) }
            val method = receiver[ir.method]  ?: throw error(
                ir,
                "Undefined method.",
                "The method ${ir.method.name}(${ir.method.parameters.map { it.second }.joinToString(", ")}) is not defined in ${receiver.type.base.name}.",
            )
            for (i in arguments.indices) {
                require(arguments[i].type.isSubtypeOf(method.parameters[i].second)) { error(
                    ir.arguments[i],
                    "Invalid method argument type.",
                    "The method ${receiver.type.base.name}.${method.name}(${method.parameters.map { it.second }.joinToString(", ")}) requires argument ${i} to be type ${method.parameters[i].second}, but received ${arguments[i].type}.",
                ) }
            }
            val result = trace("${receiver.type.base.name}.${method.name}(${method.parameters.map { it.second }.joinToString(", ")})", ir.context.firstOrNull()) {
                method.invoke(arguments)
            }
            return if (ir.cascade) receiver else result
        }
    }

    override fun visit(ir: RhovasIr.Expression.Invoke.Pipeline): Object {
        val function = when (ir.function) {
            is Function.Definition -> ir.function
            is Function.Declaration -> scope.functions[ir.function.name, ir.function.parameters.map { it.second }]!!
        }
        val receiver = visit(ir.receiver)
        return if (ir.coalesce && receiver.type.isSubtypeOf(Library.TYPES["Null"]!!)) {
            Object(Library.TYPES["Null"]!!, null)
        } else {
            val arguments = listOf(receiver) + ir.arguments.map { visit(it) }
            for (i in arguments.indices) {
                //TODO: Include qualifier for error message
                require(arguments[i].type.isSubtypeOf(ir.function.parameters[i].second)) { error(
                    ir.arguments[i],
                    "Invalid function argument type.",
                    "The function ${ir.function.name}(${ir.function.parameters.map { it.second }.joinToString(", ")}) requires argument ${i} to be type ${ir.function.parameters[i].second}, but received ${arguments[i].type}.",
                ) }
            }
            val result = trace("Source.${ir.function.name}(${ir.function.parameters.map { it.second }.joinToString(", ")})", ir.context.firstOrNull()) {
                function.invoke(arguments)
            }
            return if (ir.cascade) receiver else result
        }
    }

    override fun visit(ir: RhovasIr.Expression.Lambda): Object {
        //TODO: Limit access to variables defined in the scope after this lambda at runtime?
        return Object(
            Type.Reference(Library.TYPES["Lambda"]!!.base, listOf(Library.TYPES["Dynamic"]!!, Library.TYPES["Dynamic"]!!)),
            Lambda(ir, scope, this)
        )
    }

    override fun visit(ir: RhovasIr.Pattern.Variable): Object {
        ir.variable?.let {
            val variable = Variable.Definition(it)
            variable.value = patternState.value
            patternState.scope.variables.define(variable)
        }
        return Object(Library.TYPES["Boolean"]!!, true)
    }

    override fun visit(ir: RhovasIr.Pattern.Value): Object {
        val value = visit(ir.value)
        //TODO: Equatable<T> interface
        val method = value.methods["==", listOf(value.type)] ?: throw error(
            ir,
            "Undefined method.",
            "The method op==(${value.type}) is not defined in ${value.type.base.name}.",
        )
        val result = if (patternState.value.type.isSubtypeOf(method.parameters[0].second)) {
            trace("${value.type.base.name}.${method.name}(${method.parameters.map { it.second }.joinToString(", ")})", ir.context.firstOrNull()) {
                method.invoke(listOf(patternState.value)).value as Boolean
            }
        } else false
        return Object(Library.TYPES["Boolean"]!!, result)
    }

    override fun visit(ir: RhovasIr.Pattern.Predicate): Object {
        var result = visit(ir.pattern)
        if (result.value as Boolean) {
            //TODO: Variable bindings
            result = visit(ir.predicate)
        }
        return result
    }

    override fun visit(ir: RhovasIr.Pattern.OrderedDestructure): Object {
        if (!patternState.value.type.isSubtypeOf(Library.TYPES["List"]!!)) {
            return Object(Library.TYPES["Boolean"]!!, false)
        }
        val list = patternState.value.value as List<Object>
        var i = 0
        for (pattern in ir.patterns) {
            val value = if (pattern is RhovasIr.Pattern.VarargDestructure) {
                val value = list.subList(i, list.size - ir.patterns.size + i + 1)
                i += value.size
                Object(Library.TYPES["List"]!!, value)
            } else {
                list.getOrNull(i++) ?: return Object(Library.TYPES["Boolean"]!!, false)
            }
            patternState = patternState.copy(value = value)
            val result = visit(pattern)
            if (!(result.value as Boolean)) {
                return result
            }
        }
        if (i != list.size) {
            return Object(Library.TYPES["Boolean"]!!, false)
        }
        return Object(Library.TYPES["Boolean"]!!, true)
    }

    override fun visit(ir: RhovasIr.Pattern.NamedDestructure): Object {
        if (patternState.value.type != Library.TYPES["Object"]) {
            return Object(Library.TYPES["Boolean"]!!, false)
        }
        val map = patternState.value.value as Map<String, Object>
        val named = ir.patterns.map { it.first }.toSet()
        var vararg = false
        for ((key, pattern) in ir.patterns) {
            val value = if (pattern is RhovasIr.Pattern.VarargDestructure) {
                vararg = true
                Object(Library.TYPES["Object"]!!, map.filterKeys { !named.contains(it) })
            } else {
                map[key] ?: return Object(Library.TYPES["Boolean"]!!, false)
            }
            if (key != null) {
                val variable = Variable.Definition(Variable.Declaration(key, value.type, false))
                variable.value = value
                patternState.scope.variables.define(variable)
            }
            patternState = patternState.copy(value = value)
            if (!(visit(pattern).value as Boolean)) {
                return Object(Library.TYPES["Boolean"]!!, false)
            }
        }
        if (!vararg && map.size != named.size) {
            return Object(Library.TYPES["Boolean"]!!, false)
        }
        return Object(Library.TYPES["Boolean"]!!, true)
    }

    override fun visit(ir: RhovasIr.Pattern.TypedDestructure): Object {
        if (!patternState.value.type.isSubtypeOf(ir.type)) {
            return Object(Library.TYPES["Boolean"]!!, false)
        }
        return ir.pattern?.let { visit(it) } ?: Object(Library.TYPES["Boolean"]!!, true)
    }

    override fun visit(ir: RhovasIr.Pattern.VarargDestructure): Object {
        if (patternState.value.type.isSubtypeOf(Library.TYPES["List"]!!)) {
            val list = patternState.value.value as List<Object>
            if (ir.operator == "+" && list.isEmpty()) {
                return Object(Library.TYPES["Boolean"]!!, false)
            }
            return if (ir.pattern is RhovasIr.Pattern.Variable) {
                ir.pattern.variable?.let {
                    val variable = Variable.Definition(it)
                    variable.value = Object(Library.TYPES["List"]!!, list)
                    scope.variables.define(variable)
                }
                Object(Library.TYPES["Boolean"]!!, true)
            } else {
                //TODO: Handle variable bindings
                Object(Library.TYPES["Boolean"]!!, list.all {
                    patternState = patternState.copy(value = it)
                    ir.pattern?.let { visit(it).value as Boolean } ?: true
                })
            }
        } else if (patternState.value.type.isSubtypeOf(Library.TYPES["Object"]!!)) {
            val map = patternState.value.value as Map<String, Object>
            if (ir.operator == "+" && map.isEmpty()) {
                return Object(Library.TYPES["Boolean"]!!, false)
            }
            return if (ir.pattern is RhovasIr.Pattern.Variable) {
                ir.pattern.variable?.let {
                    val variable = Variable.Definition(it)
                    variable.value = Object(Library.TYPES["Object"]!!, map)
                    scope.variables.define(variable)
                }
                Object(Library.TYPES["Boolean"]!!, true)
            } else {
                //TODO: Handle variable bindings
                Object(Library.TYPES["Boolean"]!!, map.all {
                    //TODO: Consider allowing matching on key
                    patternState = patternState.copy(value = it.value)
                    ir.pattern?.let { visit(it).value as Boolean } ?: true
                })
            }
        } else {
            return Object(Library.TYPES["Boolean"]!!, false)
        }
    }

    override fun visit(ir: RhovasIr.Type): Object {
        return Object(Library.TYPES["Type"]!!, ir.type)
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
        //TODO: RhovasIr context
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
            listOf(), //TODO context
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
            //TODO: Use returns parameter
            //TODO: Lambda identification information for errors
            //TODO: Expected count depends on lambda invocation context (direct vs indirect)
            return evaluator.scoped(Scope.Definition(scope)) {
                if (ast.parameters.isNotEmpty()) {
                    val parameters = ast.parameters.map {
                        Pair(it.first, it.second?.let { evaluator.visit(it).value as Type } ?: Library.TYPES["Any"]!!)
                    }
                    for (i in parameters.indices) {
                        val variable = Variable.Definition(Variable.Declaration(parameters[i].first, parameters[i].second, false))
                        val value = arguments[i].third
                        evaluator.scope.variables.define(variable)
                    }
                } else if (arguments.size == 1) {
                    //TODO: entry name is (intentionally) unused
                    val variable = Variable.Definition(Variable.Declaration("val", arguments[0].second, false))
                    variable.value = arguments[0].third
                    evaluator.scope.variables.define(variable)
                } else {
                    val variable = Variable.Definition(Variable.Declaration("val", Library.TYPES["Object"]!!, false) )
                    variable.value = Object(Library.TYPES["Object"]!!, arguments.associate { it.first to it.third })
                    evaluator.scope.variables.define(variable)
                }
                try {
                    evaluator.visit(ast.body)
                    Object(Library.TYPES["Void"]!!, Unit)
                } catch (e: Return) {
                    e.value ?: Object(Library.TYPES["Void"]!!, Unit)
                }
            }
        }

    }

    data class PatternState(
        val scope: Scope.Definition,
        val value: Object,
    )

}
