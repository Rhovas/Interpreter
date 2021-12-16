package dev.rhovas.interpreter.evaluator

import dev.rhovas.interpreter.environment.*
import dev.rhovas.interpreter.environment.Function
import dev.rhovas.interpreter.library.Library
import dev.rhovas.interpreter.parser.Input
import dev.rhovas.interpreter.parser.rhovas.RhovasAst
import java.math.BigDecimal
import java.math.BigInteger
import java.util.function.Predicate

class Evaluator(private var scope: Scope) : RhovasAst.Visitor<Object> {

    private var label: String? = null
    private lateinit var patternState: PatternState
    private var stacktrace = ArrayDeque<StackFrame>().also {
        it.addLast(StackFrame("Source", Input.Range(0, 1, 0, 0)))
    }

    override fun visit(ast: RhovasAst.Source): Object {
        ast.statements.forEach { visit(it) }
        return Object(Library.TYPES["Void"]!!, Unit)
    }

    override fun visit(ast: RhovasAst.Statement.Block): Object {
        scoped(Scope(scope)) {
            ast.statements.forEach { visit(it) }
        }
        return Object(Library.TYPES["Void"]!!, Unit)
    }

    override fun visit(ast: RhovasAst.Statement.Expression): Object {
        visit(ast.expression)
        return Object(Library.TYPES["Void"]!!, Unit)
    }

    override fun visit(ast: RhovasAst.Statement.Function): Object {
        val current = scope
        val function = Function(
            ast.name,
            //TODO: Consider including name in function parameters
            ast.parameters.map { it.second?.let { visit(it).value as Type } ?: Library.TYPES["Any"]!! },
            ast.returns?.let { visit(it).value as Type } ?: Library.TYPES["Any"]!!,
        )
        function.implementation = { arguments ->
            scoped(current) {
                for (i in function.parameters.indices) {
                    val variable = Variable(ast.parameters[i].first, function.parameters[i])
                    variable.value = arguments[i]
                    scope.variables.define(variable)
                }
                try {
                    visit(ast.body)
                    Object(Library.TYPES["Void"]!!, Unit)
                } catch (e: Return) {
                    e.value ?: Object(Library.TYPES["Void"]!!, Unit)
                }
            }
        }
        scope.functions.define(function)
        return Object(Library.TYPES["Void"]!!, Unit)
    }

    override fun visit(ast: RhovasAst.Statement.Declaration): Object {
        val type = ast.type?.let { visit(it).value as Type } ?: Library.TYPES["Any"]!!
        val value = ast.value?.let { visit(it) } ?: Object(Library.TYPES["Null"]!!, null)
        val variable = Variable(ast.name, type)
        variable.value = value
        scope.variables.define(variable)
        return Object(Library.TYPES["Void"]!!, Unit)
    }

    override fun visit(ast: RhovasAst.Statement.Assignment): Object {
        when (ast.receiver) {
            is RhovasAst.Expression.Access.Variable -> {
                val variable = scope.variables[ast.receiver.name]!!
                variable.set(visit(ast.value))
            }
            is RhovasAst.Expression.Access.Property -> {
                val receiver = visit(ast.receiver.receiver)
                val property = receiver.properties[ast.receiver.name]!!
                val value = visit(ast.value)
                trace("${receiver.type.name}.${ast.receiver.name}/1", ast.context.first()) {
                    property.set(receiver, value)
                }
            }
            is RhovasAst.Expression.Access.Index -> {
                val receiver = visit(ast.receiver.receiver)
                val method = receiver.methods["[]=", ast.receiver.arguments.size + 1]!!
                invoke(method, receiver, ast.receiver.arguments + listOf(ast.value), ast)
            }
        }
        return Object(Library.TYPES["Void"]!!, Unit)
    }

    override fun visit(ast: RhovasAst.Statement.If): Object {
        val condition = visit(ast.condition)
        if (condition.value as Boolean) {
            visit(ast.thenStatement)
        } else if (ast.elseStatement != null) {
            visit(ast.elseStatement)
        }
        return Object(Library.TYPES["Void"]!!, Unit)
    }

    override fun visit(ast: RhovasAst.Statement.Match.Conditional): Object {
        val predicate = Predicate<RhovasAst> {
            val condition = visit(it)
            condition.value as Boolean
        }
        val case = ast.cases.firstOrNull { predicate.test(it.first) }
            ?: ast.elseCase?.also {
                require(it.first == null || predicate.test(it.first!!)) { error(
                    ast.elseCase.first,
                    "Failed match else assertion.",
                    "A condition match statement requires the else condition to be true.",
                ) }
            }
        case?.let { visit(it.second) }
        return Object(Library.TYPES["Void"]!!, Unit)
    }

    override fun visit(ast: RhovasAst.Statement.Match.Structural): Object {
        val argument = visit(ast.argument)
        val predicate = Predicate<RhovasAst.Pattern> {
            patternState = PatternState(Scope(this.scope), argument)
            scoped(patternState.scope) {
                visit(it).value as Boolean
            }
        }
        val case = ast.cases.firstOrNull { predicate.test(it.first) }
            ?: ast.elseCase?.also {
                require(predicate.test(it.first ?: RhovasAst.Pattern.Variable("_"))) { error(
                    ast.elseCase.first,
                    "Failed match else assertion.",
                    "A structural match statement requires the else pattern to match.",
                ) }
            }
            ?: throw error(
                ast,
                "Non-exhaustive structural match patterns.",
                "A structural match statements requires the patterns to be exhaustive, but no pattern matched argument ${argument.methods["toString", 0]!!.invoke(argument, listOf()).value as String}.",
            )
        scoped(patternState.scope) {
            visit(case.second)
        }
        return Object(Library.TYPES["Void"]!!, Unit)
    }

    override fun visit(ast: RhovasAst.Statement.For): Object {
        val iterable = visit(ast.argument)
        val label = this.label
        this.label = null
        for (element in iterable.value as List<Object>) {
            try {
                scoped(Scope(scope)) {
                    //TODO: Generic types
                    val variable = Variable(ast.name, Library.TYPES["Any"]!!)
                    variable.value = element
                    scope.variables.define(variable)
                    visit(ast.body)
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

    override fun visit(ast: RhovasAst.Statement.While): Object {
        val label = this.label
        this.label = null
        while (true) {
            val condition = visit(ast.condition)
            if (condition.value as Boolean) {
                try {
                    scoped(Scope(scope)) {
                        visit(ast.body)
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

    override fun visit(ast: RhovasAst.Statement.Try): Object {
        try {
            visit(ast.body)
        } catch (e: Throw) {
            //TODO: Catch exception types
            ast.catches.firstOrNull()?.let {
                scoped(Scope(scope)) {
                    //TODO: Exception types
                    val variable = Variable(it.name, Library.TYPES["Exception"]!!)
                    variable.value = e.exception
                    scope.variables.define(variable)
                    visit(it.body)
                }
            }
        } finally {
            //TODO: Ensure finally doesn't run for internal exceptions
            ast.finallyStatement?.let { visit(it) }
        }
        return Object(Library.TYPES["Void"]!!, Unit)
    }

    override fun visit(ast: RhovasAst.Statement.Try.Catch): Object {
        throw UnsupportedOperationException()
    }

    override fun visit(ast: RhovasAst.Statement.With): Object {
        TODO()
    }

    override fun visit(ast: RhovasAst.Statement.Label): Object {
        val label = label
        this.label = ast.label
        try {
            visit(ast.statement)
        } finally {
            this.label = label
        }
        return Object(Library.TYPES["Void"]!!, Unit)
    }

    override fun visit(ast: RhovasAst.Statement.Break): Object {
        throw Break(ast.label)
    }

    override fun visit(ast: RhovasAst.Statement.Continue): Object {
        throw Continue(ast.label)
    }

    override fun visit(ast: RhovasAst.Statement.Return): Object {
        throw Return(ast, ast.value?.let { visit(it) })
    }

    override fun visit(ast: RhovasAst.Statement.Throw): Object {
        throw Throw(visit(ast.exception))
    }

    override fun visit(ast: RhovasAst.Statement.Assert): Object {
        val condition = visit(ast.condition)
        require(condition.value as Boolean) { error(
            ast,
            "Failed assertion",
            "The assertion failed" + ast.message?.let { " (${visit(it).value})" } + ".",
        ) }
        return Object(Library.TYPES["Void"]!!, Unit)
    }

    override fun visit(ast: RhovasAst.Statement.Require): Object {
        val condition = visit(ast.condition)
        require(condition.value as Boolean) { error(
            ast,
            "Failed precondition assertion.",
            "The precondition assertion failed" + ast.message?.let { " (${visit(it).value})" } + ".",
        ) }
        return Object(Library.TYPES["Void"]!!, Unit)
    }

    override fun visit(ast: RhovasAst.Statement.Ensure): Object {
        val condition = visit(ast.condition)
        require(condition.value as Boolean) { error(
            ast,
            "Failed postcondition assertion.",
            "The postcondition assertion failed" + ast.message?.let { " (${visit(it).value})" } + ".",
        ) }
        return Object(Library.TYPES["Void"]!!, Unit)
    }

    override fun visit(ast: RhovasAst.Expression.Literal): Object {
        return when (ast.value) {
            null -> Object(Library.TYPES["Null"]!!, null)
            is Boolean -> Object(Library.TYPES["Boolean"]!!, ast.value)
            is BigInteger -> Object(Library.TYPES["Integer"]!!, ast.value)
            is BigDecimal -> Object(Library.TYPES["Decimal"]!!, ast.value)
            is String -> Object(Library.TYPES["String"]!!, ast.value)
            is RhovasAst.Atom -> Object(Library.TYPES["Atom"]!!, ast.value)
            is List<*> -> {
                val value = (ast.value as List<RhovasAst.Expression>).map { visit(it) }
                Object(Library.TYPES["List"]!!, value.toMutableList())
            }
            is Map<*, *> -> {
                val value = (ast.value as Map<String, RhovasAst.Expression>).mapValues { visit(it.value) }
                Object(Library.TYPES["Object"]!!, value.toMutableMap())
            }
            else -> throw AssertionError()
        }
    }

    override fun visit(ast: RhovasAst.Expression.Group): Object {
        return visit(ast.expression)
    }

    override fun visit(ast: RhovasAst.Expression.Unary): Object {
        val expression = visit(ast.expression)
        val method = expression.methods[ast.operator, 0]!!
        return method.invoke(expression, listOf())
    }

    override fun visit(ast: RhovasAst.Expression.Binary): Object {
        val left = visit(ast.left)
        return when (ast.operator) {
            "||" -> {
                if (left.value as Boolean) {
                    Object(Library.TYPES["Boolean"]!!, true)
                } else {
                    val right = visit(ast.right)
                    Object(Library.TYPES["Boolean"]!!, right.value as Boolean)
                }
            }
            "&&" -> {
                if (left.value as Boolean) {
                    val right = visit(ast.right)
                    Object(Library.TYPES["Boolean"]!!, right.value as Boolean)
                } else {
                    Object(Library.TYPES["Boolean"]!!, false)
                }
            }
            "==", "!=" -> {
                val method = left.methods["==", 1]!!
                val right = visit(ast.right)
                val result = if (right.type.isSubtypeOf(method.parameters[0])) {
                    method.invoke(left, listOf(right)).value as Boolean
                } else false
                val value = if (ast.operator == "==") result else !result
                Object(Library.TYPES["Boolean"]!!, value)
            }
            "===", "!==" -> {
                val right = visit(ast.right)
                //TODO: Implementation non-primitives (Integer/Decimal/String/Atom)
                val result = if (left.type == right.type) left.value === right.value else false
                val value = if (ast.operator == "===") result else !result
                Object(Library.TYPES["Boolean"]!!, value)
            }
            "<", ">", "<=", ">=" -> {
                val method = left.methods["<=>", 1]!!
                val result = invoke(method, left, listOf(ast.right), ast).value as BigInteger
                val value = when (ast.operator) {
                    "<" -> result < BigInteger.ZERO
                    ">" -> result > BigInteger.ZERO
                    "<=" -> result <= BigInteger.ZERO
                    ">=" -> result >= BigInteger.ZERO
                    else -> throw AssertionError()
                }
                Object(Library.TYPES["Boolean"]!!, value)
            }
            "+", "-", "*", "/" -> {
                val method = left.methods[ast.operator, 1]!!
                invoke(method, left, listOf(ast.right), ast)
            }
            else -> throw AssertionError()
        }
    }

    override fun visit(ast: RhovasAst.Expression.Access.Variable): Object {
        val variable = scope.variables[ast.name]!!
        return variable.get()
    }

    override fun visit(ast: RhovasAst.Expression.Access.Property): Object {
        val receiver = visit(ast.receiver)
        return if (ast.coalesce && receiver.type.isSubtypeOf(Library.TYPES["Null"]!!)) {
            receiver
        } else {
            val property = receiver.properties[ast.name]!!
            property.get(receiver)
        }
    }

    override fun visit(ast: RhovasAst.Expression.Access.Index): Object {
        val receiver = visit(ast.receiver)
        val method = receiver.methods["[]", ast.arguments.size]!!
        return invoke(method, receiver, ast.arguments, ast)
    }

    override fun visit(ast: RhovasAst.Expression.Invoke.Function): Object {
        val function = scope.functions[ast.name, ast.arguments.size]!!
        return invoke(function, ast.arguments, ast)
    }

    override fun visit(ast: RhovasAst.Expression.Invoke.Method): Object {
        val receiver = visit(ast.receiver)
        return if (ast.coalesce && receiver.type.isSubtypeOf(Library.TYPES["Null"]!!)) {
            Object(Library.TYPES["Null"]!!, null)
        } else {
            val method = receiver.methods[ast.name, ast.arguments.size]!!
            val result = invoke(method, receiver, ast.arguments, ast)
            return if (ast.cascade) receiver else result
        }
    }

    override fun visit(ast: RhovasAst.Expression.Invoke.Pipeline): Object {
        val receiver = visit(ast.receiver)
        return if (ast.coalesce && receiver.type.isSubtypeOf(Library.TYPES["Null"]!!)) {
            Object(Library.TYPES["Null"]!!, null)
        } else {
            val result = if (ast.qualifier == null) {
                val function = scope.functions[ast.name, ast.arguments.size + 1]!!
                val arguments = listOf(receiver) + ast.arguments.map { visit(it) }
                trace("Source.${function.name}/${arguments.size}", ast.context.first()) {
                    function.invoke(arguments)
                }
            } else {
                val qualifier = visit(ast.qualifier)
                val method = qualifier.methods[ast.name, ast.arguments.size + 1]!!
                val arguments = listOf(receiver) + ast.arguments.map { visit(it) }
                trace("${qualifier.type.name}.${method.name}/${arguments.size}", ast.context.first()) {
                    method.invoke(qualifier, arguments)
                }
            }
            return if (ast.cascade) receiver else result
        }
    }

    override fun visit(ast: RhovasAst.Expression.Lambda): Object {
        //TODO: Limit access to variables defined in the scope after this lambda at runtime?
        return Object(Library.TYPES["Lambda"]!!, Lambda(ast, scope, this))
    }

    override fun visit(ast: RhovasAst.Expression.Macro): Object {
        TODO()
    }

    override fun visit(ast: RhovasAst.Expression.Dsl): Object {
        TODO()
    }

    override fun visit(ast: RhovasAst.Expression.Interpolation): Object {
        TODO()
    }

    override fun visit(ast: RhovasAst.Pattern.Variable): Object {
        if (ast.name != "_") {
            val variable = Variable(ast.name, patternState.value.type)
            variable.value = patternState.value
            patternState.scope.variables.define(variable)
        }
        return Object(Library.TYPES["Boolean"]!!, true)
    }

    override fun visit(ast: RhovasAst.Pattern.Value): Object {
        val value = visit(ast.value)
        val method = value.methods["==", 1]!!
        val result = if (value.type == patternState.value.type) method.invoke(value, listOf(patternState.value)).value as Boolean else false
        return Object(Library.TYPES["Boolean"]!!, result)
    }

    override fun visit(ast: RhovasAst.Pattern.Predicate): Object {
        var result = visit(ast.pattern)
        if (result.value as Boolean) {
            result = visit(ast.predicate)
        }
        return result
    }

    override fun visit(ast: RhovasAst.Pattern.OrderedDestructure): Object {
        if (patternState.value.type != Library.TYPES["List"]) {
            return Object(Library.TYPES["Boolean"]!!, false)
        }
        val list = patternState.value.value as List<Object>
        var i = 0
        for (pattern in ast.patterns) {
            val value = if (pattern is RhovasAst.Pattern.VarargDestructure) {
                val value = list.subList(i, list.size - ast.patterns.size + i + 1)
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

    override fun visit(ast: RhovasAst.Pattern.NamedDestructure): Object {
        if (patternState.value.type != Library.TYPES["Object"]) {
            return Object(Library.TYPES["Boolean"]!!, false)
        }
        val map = patternState.value.value as Map<String, Object>
        val named = ast.patterns.map { it.first }.toSet()
        var vararg = false
        for ((key, pattern) in ast.patterns) {
            val value = if (pattern is RhovasAst.Pattern.VarargDestructure) {
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
                val variable = Variable(key, value.type)
                variable.value = value
                patternState.scope.variables.define(variable)
            }
        }
        if (!vararg && map.size != named.size) {
            return Object(Library.TYPES["Boolean"]!!, false)
        }
        return Object(Library.TYPES["Boolean"]!!, true)
    }

    override fun visit(ast: RhovasAst.Pattern.TypedDestructure): Object {
        if (patternState.value.type != visit(ast.type).value as Type) {
            return Object(Library.TYPES["Boolean"]!!, false)
        }
        return ast.pattern?.let { visit(it) } ?: Object(Library.TYPES["Boolean"]!!, true)
    }

    override fun visit(ast: RhovasAst.Pattern.VarargDestructure): Object {
        if (patternState.value.type.isSubtypeOf(Library.TYPES["List"]!!)) {
            val list = patternState.value.value as List<Object>
            if (ast.operator == "+" && list.isEmpty()) {
                return Object(Library.TYPES["Boolean"]!!, false)
            }
            return if (ast.pattern is RhovasAst.Pattern.Variable) {
                val variable = Variable(ast.pattern.name, Library.TYPES["List"]!!)
                variable.value = Object(Library.TYPES["List"]!!, list)
                scope.variables.define(variable)
                Object(Library.TYPES["Boolean"]!!, true)
            } else {
                //TODO: Handle variable bindings
                Object(Library.TYPES["Boolean"]!!, list.all {
                    patternState = patternState.copy(value = it)
                    ast.pattern?.let { visit(it).value as Boolean } ?: true
                })
            }
        } else if (patternState.value.type.isSubtypeOf(Library.TYPES["Object"]!!)) {
            val map = patternState.value.value as Map<String, Object>
            if (ast.operator == "+" && map.isEmpty()) {
                return Object(Library.TYPES["Boolean"]!!, false)
            }
            return if (ast.pattern is RhovasAst.Pattern.Variable) {
                val variable = Variable(ast.pattern.name, Library.TYPES["Object"]!!)
                variable.value = Object(Library.TYPES["Object"]!!, map)
                scope.variables.define(variable)
                Object(Library.TYPES["Boolean"]!!, true)
            } else {
                //TODO: Handle variable bindings
                Object(Library.TYPES["Boolean"]!!, map.all {
                    //TODO: Consider allowing matching on key
                    patternState = patternState.copy(value = it.value)
                    ast.pattern?.let { visit(it).value as Boolean } ?: true
                })
            }
        } else {
            return Object(Library.TYPES["Boolean"]!!, false)
        }
    }

    override fun visit(ast: RhovasAst.Type): Object {
        val type = Library.TYPES[ast.name]!!
        return Object(Library.TYPES["Type"]!!, type)
    }

    private fun invoke(function: Function, arguments: List<RhovasAst.Expression>, ast: RhovasAst): Object {
        //TODO: Evaluation/typechecking order
        val evaluated = arguments.map { visit(it) }
        //TODO: Function namespaces
        return trace("Source.${function.name}/${evaluated.size}", ast.context.first()) {
            function.invoke(evaluated)
        }
    }

    private fun invoke(method: Method, receiver: Object, arguments: List<RhovasAst.Expression>, ast: RhovasAst): Object {
        val evaluated = arguments.map { visit(it) }
        //TODO: Method namespaces (for inheritance)
        return trace("${receiver.type.name}.${method.name}/${evaluated.size}", ast.context.first()) {
            method.invoke(receiver, evaluated)
        }
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

    private fun <T> trace(source: String, range: Input.Range, block: () -> T): T {
        stacktrace.addLast(stacktrace.removeLast().copy(range = range))
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

    fun error(ast: RhovasAst?, summary: String, details: String): EvaluateException {
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

    data class Return(val ast: RhovasAst, val value: Object?): Exception()

    data class Throw(val exception: Object): Exception()

    data class Lambda(
        val ast: RhovasAst.Expression.Lambda,
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
                        val variable = Variable(parameters[i].first, parameters[i].second)
                        variable.value = arguments[i].third
                        evaluator.scope.variables.define(variable)
                    }
                } else if (arguments.size == 1) {
                    //TODO: entry name is (intentionally) unused
                    val variable = Variable("val", arguments[0].second)
                    variable.value = arguments[0].third
                    evaluator.scope.variables.define(variable)
                } else {
                    val variable = Variable("val", Library.TYPES["Object"]!!)
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
        val scope: Scope,
        val value: Object,
    )

}
