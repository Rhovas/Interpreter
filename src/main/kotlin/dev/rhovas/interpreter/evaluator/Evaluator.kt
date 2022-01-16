package dev.rhovas.interpreter.evaluator

import dev.rhovas.interpreter.analyzer.rhovas.RhovasIr
import dev.rhovas.interpreter.environment.*
import dev.rhovas.interpreter.library.Library
import dev.rhovas.interpreter.parser.Input
import dev.rhovas.interpreter.parser.rhovas.RhovasAst
import java.math.BigDecimal
import java.math.BigInteger
import java.util.function.Predicate

class Evaluator(private var scope: Scope) : RhovasIr.Visitor<Object> {

    private var label: String? = null
    private lateinit var patternState: PatternState
    private var stacktrace = ArrayDeque<StackFrame>().also {
        it.addLast(StackFrame("Source", Input.Range(0, 1, 0, 0)))
    }

    override fun visit(ir: RhovasIr.Source): Object {
        ir.statements.forEach { visit(it) }
        return Object(Library.TYPES["Void"]!!, Unit)
    }

    override fun visit(ir: RhovasIr.Statement.Block): Object {
        scoped(Scope(scope)) {
            ir.statements.forEach { visit(it) }
        }
        return Object(Library.TYPES["Void"]!!, Unit)
    }

    override fun visit(ir: RhovasIr.Statement.Expression): Object {
        visit(ir.expression)
        return Object(Library.TYPES["Void"]!!, Unit)
    }

    override fun visit(ir: RhovasIr.Statement.Function): Object {
        val current = scope
        ir.function.implementation = { arguments ->
            scoped(current) {
                for (i in ir.function.parameters.indices) {
                    //TODO: Variable.Parameter class?
                    val parameter = Variable.Local(ir.function.parameters[i].first, ir.function.parameters[i].second, false)
                    scope.variables.define(Variable.Local.Runtime(parameter, arguments[i]))
                }
                try {
                    visit(ir.body)
                    Object(Library.TYPES["Void"]!!, Unit)
                } catch (e: Return) {
                    e.value ?: Object(Library.TYPES["Void"]!!, Unit)
                }
            }
        }
        scope.functions.define(ir.function)
        return Object(Library.TYPES["Void"]!!, Unit)
    }

    override fun visit(ir: RhovasIr.Statement.Declaration): Object {
        val value = ir.value?.let { visit(it) } ?: Object(Library.TYPES["Null"]!!, null)
        scope.variables.define(Variable.Local.Runtime(ir.variable, value))
        return Object(Library.TYPES["Void"]!!, Unit)
    }

    override fun visit(ir: RhovasIr.Statement.Assignment.Variable): Object {
        val variable = scope.variables[ir.variable.name]!! as Variable.Local.Runtime
        require(variable.mutable) { error(
            ir,
            "Unassignable variable.",
            "The variable ${variable.name} is unassignable.",
        ) }
        variable.value = visit(ir.value)
        return Object(Library.TYPES["Void"]!!, Unit)
    }

    override fun visit(ir: RhovasIr.Statement.Assignment.Property): Object {
        val receiver = visit(ir.receiver)
        val value = visit(ir.value)
        //TODO: Specification for checking properties (should getter be enforced?)
        val property = receiver[ir.property] ?: throw error(
            ir,
            "Undefined property.",
            "The property ${receiver.type.base.name}.${ir.property.name} is undefined.",
        )
        val method = property.setter ?: throw error(
            ir,
            "Unassignable property.",
            "The property ${receiver.type.base.name}.${ir.property.name} is unassignable.",
        )
        require(value.type.isSubtypeOf(method.parameters[0].second)) { error(
            ir.value,
            "Invalid property value type.",
            "The property ${receiver.type.base.name}.${method.name} requires the value to be type ${method.parameters[0].second}, but received ${value.type}.",
        ) }
        trace("${receiver.type.base.name}.${method.name}/${method.parameters.size}", ir.context?.first()) {
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
            "The method ${receiver.type.base.name}.${ir.method.name}/${ir.method.parameters.size} is undefined.",
        )
        for (i in arguments.indices) {
            require(arguments[i].type.isSubtypeOf(method.parameters[i].second)) { error(
                ir.arguments.getOrNull(i) ?: ir.value,
                "Invalid method argument type.",
                "The method ${receiver.type.base.name}.${method.name}/${method.parameters.size} requires argument ${i} to be type ${method.parameters[i].second}, but received ${arguments[i].type}.",
            ) }
        }
        return trace("${receiver.type.base.name}.${method.name}/${method.parameters.size}", ir.context?.first()) {
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
        val predicate = Predicate<RhovasIr.Pattern> {
            patternState = PatternState(Scope(this.scope), argument)
            scoped(patternState.scope) {
                visit(it).value as Boolean
            }
        }
        val case = ir.cases.firstOrNull { predicate.test(it.first) }
            ?: ir.elseCase?.also {
                require(predicate.test(it.first ?: RhovasIr.Pattern.Variable(Variable.Local("_", argument.type, false)))) { error(
                    ir.elseCase.first,
                    "Failed match else assertion.",
                    "A structural match statement requires the else pattern to match.",
                ) }
            }
            ?: throw error(
                ir,
                "Non-exhaustive structural match patterns.",
                "A structural match statements requires the patterns to be exhaustive, but no pattern matched argument ${argument.methods["toString", 0]!!.invoke(listOf()).value as String}.",
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
                scoped(Scope(scope)) {
                    //TODO: Generic types
                    val variable = Variable.Local(ir.name, Library.TYPES["Any"]!!, false)
                    scope.variables.define(Variable.Local.Runtime(variable, element))
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
                    scoped(Scope(scope)) {
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
            //TODO: Catch exception types
            ir.catches.firstOrNull()?.let {
                scoped(Scope(scope)) {
                    //TODO: Exception types
                    val variable = Variable.Local(it.name, Library.TYPES["Exception"]!!, false)
                    scope.variables.define(Variable.Local.Runtime(variable, e.exception))
                    visit(it.body)
                }
            }
        } finally {
            //TODO: Ensure finally doesn't run for internal exceptions
            ir.finallyStatement?.let { visit(it) }
        }
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

    override fun visit(ir: RhovasIr.Expression.Literal): Object {
        return when (ir.value) {
            null -> Object(Library.TYPES["Null"]!!, null)
            is Boolean -> Object(Library.TYPES["Boolean"]!!, ir.value)
            is BigInteger -> Object(Library.TYPES["Integer"]!!, ir.value)
            is BigDecimal -> Object(Library.TYPES["Decimal"]!!, ir.value)
            is String -> Object(Library.TYPES["String"]!!, ir.value)
            is RhovasAst.Atom -> Object(Library.TYPES["Atom"]!!, ir.value)
            is List<*> -> {
                val value = (ir.value as List<RhovasIr.Expression>).map { visit(it) }
                Object(Type.Reference(Library.TYPES["List"]!!.base, listOf(Library.TYPES["Dynamic"]!!)), value.toMutableList())
            }
            is Map<*, *> -> {
                val value = (ir.value as Map<String, RhovasIr.Expression>).mapValues { visit(it.value) }
                Object(Library.TYPES["Object"]!!, value.toMutableMap())
            }
            else -> throw AssertionError()
        }
    }

    override fun visit(ir: RhovasIr.Expression.Group): Object {
        return visit(ir.expression)
    }

    override fun visit(ir: RhovasIr.Expression.Unary): Object {
        val expression = visit(ir.expression)
        val method = expression.methods[ir.operator, 0] ?: throw error(
            ir,
            "Undefined method.",
            "The method ${expression.type.base.name}.${ir.operator}/0 is undefined.",
        )
        return trace("${expression.type.base.name}.${ir.operator}/0", ir.context?.first()) {
            method.invoke(listOf())
        }
    }

    override fun visit(ir: RhovasIr.Expression.Binary): Object {
        val left = visit(ir.left)
        return when (ir.operator) {
            "||" -> Object(Library.TYPES["Boolean"]!!, left.value as Boolean || visit(ir.right).value as Boolean)
            "&&" -> Object(Library.TYPES["Boolean"]!!, left.value as Boolean && visit(ir.right).value as Boolean)
            "==", "!=" -> {
                val right = visit(ir.right)
                val method = left.methods["==", 1] ?: throw error(
                    ir,
                    "Undefined method.",
                    "The method ${left.type.base.name}.==/1 is undefined.",
                )
                val result = if (right.type.isSubtypeOf(method.parameters[0].second)) {
                    trace("${left.type.base.name}.${method.name}/${method.parameters.size}", ir.context?.first()) {
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
                val right = visit(ir.right)
                val method = left.methods["<=>", 1] ?: throw error(
                    ir,
                    "Undefined method.",
                    "The method ${left.type.base.name}.<=>/1 is undefined.",
                )
                require(right.type.isSubtypeOf(method.parameters[0].second)) { error(
                    ir.right,
                    "Invalid method argument type.",
                    "The method ${left.type.base.name}.${method.name}/${method.parameters.size} requires argument 0 to be type ${method.parameters[0].second}, but received ${right.type}."
                ) }
                val result = trace("${left.type.base.name}.${method.name}/${method.parameters.size}", ir.context?.first()) {
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
                val right = visit(ir.right)
                val method = left.methods[ir.operator, 1] ?: throw error(
                    ir,
                    "Undefined method.",
                    "The method ${left.type.base.name}.${ir.operator}/1 is undefined.",
                )
                require(right.type.isSubtypeOf(method.parameters[0].second)) { error(
                    ir.right,
                    "Invalid method argument type.",
                    "The method ${left.type.base.name}.${method.name}/${method.parameters.size} requires argument 0 to be type ${method.parameters[0].second.base.name}, but received ${right.type.base.name}."
                ) }
                trace("${left.type.base.name}.${method.name}/${method.parameters.size}", ir.context?.first()) {
                    method.invoke(listOf(right))
                }
            }
            else -> throw AssertionError()
        }
    }

    override fun visit(ir: RhovasIr.Expression.Access.Variable): Object {
        val variable = scope.variables[ir.variable.name] as Variable.Local.Runtime
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
                "The property ${receiver.type.base.name}.${ir.property.name} is undefined.",
            )
            trace("${receiver.type.base.name}.${method.name}/${method.parameters.size}", ir.context?.first()) {
                method.invoke(listOf())
            }
        }
    }

    override fun visit(ir: RhovasIr.Expression.Access.Index): Object {
        val receiver = visit(ir.receiver)
        val arguments = ir.arguments.map { visit(it) }
        val method = receiver[ir.method]  ?: throw error(
            ir,
            "Undefined method.",
            "The method ${receiver.type.base.name}.${ir.method.name}/${ir.method.parameters.size} is undefined.",
        )
        for (i in arguments.indices) {
            require(arguments[i].type.isSubtypeOf(method.parameters[i].second)) { error(
                ir.arguments[i],
                "Invalid method argument type.",
                "The method ${receiver.type.base.name}.${method.name}/${method.parameters.size} requires argument ${i} to be type ${method.parameters[i].second}, but received ${arguments[i].type}.",
            ) }
        }
        return trace("${receiver.type.base.name}.${method.name}/${method.parameters.size}", ir.context?.first()) {
            method.invoke(arguments)
        }
    }

    override fun visit(ir: RhovasIr.Expression.Invoke.Function): Object {
        val arguments = ir.arguments.map { visit(it) }
        for (i in arguments.indices) {
            require(arguments[i].type.isSubtypeOf(ir.function.parameters[i].second)) { error(
                ir.arguments[i],
                "Invalid function argument type.",
                "The function Source.${ir.function.name}/${ir.function.parameters.size} requires argument ${i} to be type ${ir.function.parameters[i].second}, but received ${arguments[i].type}.",
            ) }
        }
        return trace("Source.${ir.function.name}/${ir.function.parameters.size}", ir.context?.first()) {
            ir.function.invoke(arguments)
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
                "The method ${receiver.type.base.name}.${ir.method.name}/${ir.method.parameters.size} is undefined.",
            )
            for (i in arguments.indices) {
                require(arguments[i].type.isSubtypeOf(method.parameters[i].second)) { error(
                    ir.arguments[i],
                    "Invalid method argument type.",
                    "The method ${receiver.type.base.name}.${method.name}/${method.parameters.size} requires argument ${i} to be type ${method.parameters[i].second}, but received ${arguments[i].type}.",
                ) }
            }
            val result = trace("${receiver.type.base.name}.${method.name}/${method.parameters.size}", ir.context?.first()) {
                method.invoke(arguments)
            }
            return if (ir.cascade) receiver else result
        }
    }

    override fun visit(ir: RhovasIr.Expression.Invoke.Pipeline): Object {
        val receiver = visit(ir.receiver)
        return if (ir.coalesce && receiver.type.isSubtypeOf(Library.TYPES["Null"]!!)) {
            Object(Library.TYPES["Null"]!!, null)
        } else {
            val arguments = listOf(receiver) + ir.arguments.map { visit(it) }
            for (i in arguments.indices) {
                require(arguments[i].type.isSubtypeOf(ir.function.parameters[i].second)) { error(
                    ir.arguments[i],
                    "Invalid function argument type.",
                    "The function Source.${ir.function.name}/${ir.function.parameters.size} requires argument ${i} to be type ${ir.function.parameters[i].second}, but received ${arguments[i].type}.",
                ) }
            }
            val result = trace("Source.${ir.function.name}/${ir.function.parameters.size}", ir.context?.first()) {
                ir.function.invoke(arguments)
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

    override fun visit(ir: RhovasIr.Expression.Macro): Object {
        TODO()
    }

    override fun visit(ir: RhovasIr.Expression.Dsl): Object {
        TODO()
    }

    override fun visit(ir: RhovasIr.Expression.Interpolation): Object {
        TODO()
    }

    override fun visit(ir: RhovasIr.Pattern.Variable): Object {
        ir.variable?.let { patternState.scope.variables.define(Variable.Local.Runtime(it, patternState.value)) }
        return Object(Library.TYPES["Boolean"]!!, true)
    }

    override fun visit(ir: RhovasIr.Pattern.Value): Object {
        val value = visit(ir.value)
        val method = value.methods["==", 1] ?: throw error(
            ir,
            "Undefined method.",
            "The method ${value.type.base.name}.==/1 is undefined.",
        )
        val result = if (patternState.value.type.isSubtypeOf(method.parameters[0].second)) {
            trace("${value.type.base.name}.${method.name}/${method.parameters.size}", ir.context?.first()) {
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
            if (pattern != null) {
                patternState = patternState.copy(value = value)
                if (!(visit(pattern).value as Boolean)) {
                    return Object(Library.TYPES["Boolean"]!!, false)
                }
            } else {
                val variable = Variable.Local(key, value.type, false)
                patternState.scope.variables.define(Variable.Local.Runtime(variable, value))
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
                ir.pattern.variable?.let { scope.variables.define(Variable.Local.Runtime(it, Object(Library.TYPES["List"]!!, list))) }
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
                ir.pattern.variable?.let { scope.variables.define(Variable.Local.Runtime(it, Object(Library.TYPES["Object"]!!, map))) }
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

    private fun <T> scoped(scope: Scope, block: () -> T): T {
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
        val scope: Scope,
        val evaluator: Evaluator,
    ) {

        fun invoke(arguments: List<Triple<String, Type, Object>>, returns: Type): Object {
            //TODO: Lambda identification information for errors
            //TODO: Expected count depends on lambda invocation context (direct vs indirect)
            return evaluator.scoped(Scope(scope)) {
                if (ast.parameters.isNotEmpty()) {
                    val parameters = ast.parameters.map {
                        Pair(it.first, it.second?.let { evaluator.visit(it).value as Type } ?: Library.TYPES["Any"]!!)
                    }
                    for (i in parameters.indices) {
                        val variable = Variable.Local(parameters[i].first, parameters[i].second, false)
                        val value = arguments[i].third
                        evaluator.scope.variables.define(variable)
                    }
                } else if (arguments.size == 1) {
                    //TODO: entry name is (intentionally) unused
                    val variable = Variable.Local("val", arguments[0].second, false)
                    val value = arguments[0].third
                    evaluator.scope.variables.define(Variable.Local.Runtime(variable, value))
                } else {
                    val variable = Variable.Local("val", Library.TYPES["Object"]!!, false)
                    val value = Object(Library.TYPES["Object"]!!, arguments.associate { it.first to it.third })
                    evaluator.scope.variables.define(Variable.Local.Runtime(variable, value))
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
        val scope: Scope,
        val value: Object,
    )

}
