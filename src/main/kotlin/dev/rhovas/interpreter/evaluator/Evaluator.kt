package dev.rhovas.interpreter.evaluator

import dev.rhovas.interpreter.environment.*
import dev.rhovas.interpreter.environment.Function
import dev.rhovas.interpreter.library.Library
import dev.rhovas.interpreter.parser.rhovas.RhovasAst
import java.math.BigDecimal
import java.math.BigInteger
import java.util.function.Predicate

class Evaluator(private var scope: Scope) : RhovasAst.Visitor<Object> {

    private var label: String? = null
    private lateinit var patternState: PatternState

    override fun visit(ast: RhovasAst.Statement.Block): Object {
        scoped(Scope(scope)) {
            ast.statements.forEach { visit(it) }
        }
        return Object(Library.TYPES["Void"]!!, Unit)
    }

    override fun visit(ast: RhovasAst.Statement.Expression): Object {
        if (ast.expression is RhovasAst.Expression.Function) {
            visit(ast.expression)
        } else {
            throw EvaluateException("Expression statement is not supported by expression of type ${ast.javaClass.simpleName}.")
        }
        return Object(Library.TYPES["Void"]!!, Unit)
    }

    override fun visit(ast: RhovasAst.Statement.Function): Object {
        val current = scope
        val parameters = ast.parameters.map {
            Pair(it.first, it.second?.let { visit(it).value as Type } ?: Library.TYPES["Any"]!!)
        }
        val returns = ast.returns?.let { visit(it).value as Type } ?: Library.TYPES["Any"]!!
        scope.functions.define(Function(ast.name, parameters.map { it.second }, returns) { arguments ->
            scoped(current) {
                for (i in parameters.indices) {
                    if (parameters[i].second.name != "Any" && arguments[i].type.name != parameters[i].second.name) {
                        throw EvaluateException("Invalid argument to function ${ast.name}/${parameters.size}: expected ${parameters[i].second.name}, received ${arguments[i].type.name}.")
                    }
                    scope.variables.define(Variable(parameters[i].first, parameters[i].second, arguments[i]))
                }
                try {
                    visit(ast.body)
                    Object(Library.TYPES["Void"]!!, Unit)
                } catch (e: Return) {
                    if (e.value != null && returns.name != "Any" && e.value.type.name != returns.name) {
                        throw EvaluateException("Invalid return value from function ${ast.name}/${parameters.size}: expected ${returns.name}, received ${e.value.type}.")
                    }
                    e.value ?: Object(Library.TYPES["Void"]!!, Unit)
                }
            }
        })
        return Object(Library.TYPES["Void"]!!, Unit)
    }

    override fun visit(ast: RhovasAst.Statement.Declaration): Object {
        //TODO: Immutable variables
        if (!ast.mutable && ast.value == null) {
            //TODO: Semantic analysis validation
            throw EvaluateException("Immutable variable requires a value.")
        }
        //TODO: Redeclaration/shadowing
        val type = ast.type?.let { visit(it).value as Type } ?: Library.TYPES["Any"]!!
        val value = ast.value?.let { visit(it) } ?: Object(Library.TYPES["Null"]!!, null)
        if (type.name != "Any" && ast.value !== null && value.type.name != type.name) {
            //TODO: Proper handling of uninitialized variables
            throw EvaluateException("Invalid value for variable ${ast.name}: expected ${type.name}, received ${value.type.name}.")
        }
        scope.variables.define(Variable(ast.name, type, value))
        return Object(Library.TYPES["Void"]!!, Unit)
    }

    override fun visit(ast: RhovasAst.Statement.Assignment): Object {
        if (ast.receiver is RhovasAst.Expression.Access) {
            if (ast.receiver.receiver != null) {
                val receiver = visit(ast.receiver.receiver)
                val property = receiver.properties[ast.receiver.name]
                    ?: throw EvaluateException("Property ${ast.receiver.name} is not supported by type ${receiver.type.name}.")
                //TODO: Immutable properties
                property.set(visit(ast.value))
            } else {
                val variable = scope.variables[ast.receiver.name]
                    ?: throw EvaluateException("Variable ${ast.receiver.name} is not defined.")
                //TODO: Immutable variables
                variable.set(visit(ast.value))
            }
        } else if (ast.receiver is RhovasAst.Expression.Index) {
            val receiver = visit(ast.receiver.receiver)
            val method = receiver.methods["[]=", ast.receiver.arguments.size + 1]
                ?: throw EvaluateException("Method []=/${ast.receiver.arguments.size + 1} is not supported by type ${receiver.type.name}.")
            method.invoke(ast.receiver.arguments.map { visit(it) } + listOf(visit(ast.value)))
        } else {
            //TODO: Semantic analysis validation
            throw EvaluateException("Assignment is not supported by expression of type ${ast.receiver.javaClass.simpleName}.")
        }
        return Object(Library.TYPES["Void"]!!, Unit)
    }

    override fun visit(ast: RhovasAst.Statement.If): Object {
        val condition = visit(ast.condition)
        if (condition.type != Library.TYPES["Boolean"]!!) {
            throw EvaluateException("If condition is not supported by type ${condition.type}.")
        }
        if (condition.value as Boolean) {
            visit(ast.thenStatement)
        } else if (ast.elseStatement != null) {
            visit(ast.elseStatement)
        }
        return Object(Library.TYPES["Void"]!!, Unit)
    }

    override fun visit(ast: RhovasAst.Statement.Match.Conditional): Object {
        val predicate = Predicate { condition: Object ->
            if (condition.type != Library.TYPES["Boolean"]!!) {
                throw EvaluateException("Match case condition is not supported by type ${condition.type.name}.")
            }
            condition.value as Boolean
        }
        val case = ast.cases.firstOrNull { predicate.test(visit(it.first)) }
            ?: ast.elseCase?.also {
                if (it.first?.let { predicate.test(visit(it)) } == false) {
                    throw EvaluateException("Match else condition returned false.")
                }
            }
        case?.let { visit(it.second) }
        return Object(Library.TYPES["Void"]!!, Unit)
    }

    override fun visit(ast: RhovasAst.Statement.Match.Structural): Object {
        val argument = visit(ast.argument)
        val predicate = Predicate { pattern: RhovasAst.Pattern ->
            patternState = PatternState(Scope(this.scope), argument)
            scoped(patternState.scope) {
                visit(pattern).value as Boolean
            }
        }
        val case = ast.cases.firstOrNull { predicate.test(it.first) }
            ?: ast.elseCase?.also {
                if (it.first == null) {
                    patternState = PatternState(Scope(this.scope), argument)
                } else if (!predicate.test(it.first!!)) {
                    throw EvaluateException("Match else condition returned false.")
                }
            }
            ?: throw EvaluateException("Match patterns were not exhaustive.")
        return scoped(patternState?.scope) {
            visit(case.second)
        }
    }

    override fun visit(ast: RhovasAst.Statement.For): Object {
        val iterable = visit(ast.iterable)
        //TODO: Iterable type
        if (iterable.type != Library.TYPES["List"]!!) {
            throw EvaluateException("For iterable is not supported by type ${iterable.type}.")
        }
        val label = this.label
        this.label = null
        for (element in iterable.value as List<Object>) {
            try {
                scoped(Scope(scope)) {
                    //TODO: Generic types
                    scope.variables.define(Variable(ast.name, Library.TYPES["Any"]!!, element))
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
            if (condition.type != Library.TYPES["Boolean"]!!) {
                throw EvaluateException("If condition is not supported by type ${condition.type}.")
            }
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
                    scope.variables.define(Variable(it.name, Library.TYPES["Exception"]!!, e.exception))
                    visit(it.body)
                }
            }
        } finally {
            //TODO: Ensure finally doesn't run for internal exceptions
            ast.finallyStatement?.let { visit(it) }
        }
        return Object(Library.TYPES["Void"]!!, Unit)
    }

    override fun visit(ast: RhovasAst.Statement.With): Object {
        TODO()
    }

    override fun visit(ast: RhovasAst.Statement.Label): Object {
        if (ast.statement !is RhovasAst.Statement.For && ast.statement !is RhovasAst.Statement.While) {
            throw EvaluateException("Label is not supported for statement of type ${ast.statement.javaClass.simpleName}.")
        }
        val original = label
        label = ast.label
        try {
            visit(ast.statement)
        } finally {
            label = original
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
        throw Return(ast.value?.let { visit(it) })
    }

    override fun visit(ast: RhovasAst.Statement.Throw): Object {
        throw Throw(visit(ast.exception))
    }

    override fun visit(ast: RhovasAst.Statement.Assert): Object {
        TODO()
    }

    override fun visit(ast: RhovasAst.Statement.Require): Object {
        TODO()
    }

    override fun visit(ast: RhovasAst.Statement.Ensure): Object {
        TODO()
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
        val method = expression.methods[ast.operator, 0]
            ?: throw EvaluateException("Unary ${ast.operator} is not supported by type ${expression.type.name}.")
        return method.invoke(listOf())
    }

    override fun visit(ast: RhovasAst.Expression.Binary): Object {
        val left = visit(ast.left)
        return when (ast.operator) {
            "||" -> {
                if (left.type != Library.TYPES["Boolean"]!!) {
                    throw EvaluateException("Binary || is not supported by type ${left.type.name}")
                }
                if (left.value as Boolean) {
                    Object(Library.TYPES["Boolean"]!!, true)
                } else {
                    val right = visit(ast.right)
                    if (right.type != Library.TYPES["Boolean"]!!) {
                        throw EvaluateException("Binary || is not supported by type ${right.type.name}")
                    }
                    Object(Library.TYPES["Boolean"]!!, right.value as Boolean)
                }
            }
            "&&" -> {
                if (left.type != Library.TYPES["Boolean"]!!) {
                    throw EvaluateException("Binary && is not supported by type ${left.type.name}")
                }
                if (left.value as Boolean) {
                    val right = visit(ast.right)
                    if (right.type != Library.TYPES["Boolean"]!!) {
                        throw EvaluateException("Binary && is not supported by type ${right.type.name}")
                    }
                    Object(Library.TYPES["Boolean"]!!, right.value as Boolean)
                } else {
                    Object(Library.TYPES["Boolean"]!!, false)
                }
            }
            "==", "!=" -> {
                val method = left.methods["==", 1]
                    ?: throw EvaluateException("Binary ${ast.operator} is not supported by type ${left.type.name}.")
                val right = visit(ast.right)
                val result = if (left.type == right.type) method.invoke(listOf(right)).value as Boolean else false
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
                val method = left.methods["<=>", 1]
                    ?: throw EvaluateException("Binary ${ast.operator} is not supported by type ${left.type.name}.")
                val right = visit(ast.right)
                if (left.type != right.type) {
                    throw EvaluateException("Binary ${ast.operator} is not supported by type ${left.type.name} with argument ${right.type.name}")
                }
                val result = method.invoke(listOf(right)).value as BigInteger
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
                val method = left.methods[ast.operator, 1]
                    ?: throw EvaluateException("Binary ${ast.operator} is not supported by type ${left.type.name}.")
                val right = visit(ast.right)
                method.invoke(listOf(right))
            }
            else -> throw AssertionError()
        }
    }

    override fun visit(ast: RhovasAst.Expression.Access): Object {
        return if (ast.receiver != null) {
            val receiver = visit(ast.receiver)
            val property = receiver.properties[ast.name]
                ?: throw EvaluateException("Property ${ast.name} is not supported by type ${receiver.type.name}.")
            property.get()
        } else {
            val variable = scope.variables[ast.name]
                ?: throw EvaluateException("Variable ${ast.name} is not defined.")
            variable.get()
        }
    }

    override fun visit(ast: RhovasAst.Expression.Index): Object {
        val receiver = visit(ast.receiver)
        val method = receiver.methods["[]", ast.arguments.size]
            ?: throw EvaluateException("Method []/${ast.arguments.size} is not supported by type ${receiver.type.name}.")
        return method.invoke(ast.arguments.map { visit(it) })
    }

    override fun visit(ast: RhovasAst.Expression.Function): Object {
        return if (ast.receiver != null) {
            val receiver = visit(ast.receiver)
            val method = receiver.methods[ast.name, ast.arguments.size]
                ?: throw EvaluateException("Method ${ast.name}/${ast.arguments.size} is not supported by type ${receiver.type.name}.")
            method.invoke(ast.arguments.map { visit(it) })
        } else {
            val function = scope.functions[ast.name, ast.arguments.size]
                ?: throw EvaluateException("Function ${ast.name}/${ast.arguments.size} is not defined.")
            function.invoke(ast.arguments.map { visit(it) })
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

    override fun visit(ast: RhovasAst.Pattern.Variable): Object {
        if (ast.name != "_") {
            patternState.scope.variables.define(Variable(ast.name, patternState.value.type, patternState.value))
        }
        return Object(Library.TYPES["Boolean"]!!, true)
    }

    override fun visit(ast: RhovasAst.Pattern.Value): Object {
        val value = visit(ast.value)
        val method = value.methods["==", 1]
            ?: throw EvaluateException("Binary == is not supported by type ${value.type.name}.")
        val result = if (value.type == patternState.value.type) method.invoke(listOf(patternState.value)).value as Boolean else false
        return Object(Library.TYPES["Boolean"]!!, result)
    }

    override fun visit(ast: RhovasAst.Pattern.Predicate): Object {
        var result = visit(ast.pattern)
        if (result.value as Boolean) {
            result = visit(ast.predicate)
            if (result.type != Library.TYPES["Boolean"]!!) {
                throw EvaluateException("Predicate pattern is not supported by type ${result.type}.")
            }
        }
        return result
    }

    override fun visit(ast: RhovasAst.Pattern.OrderedDestructure): Object {
        if (patternState.value.type != Library.TYPES["List"]) {
            return Object(Library.TYPES["Boolean"]!!, false)
        }
        val list = patternState.value.value as List<Object>
        var i = 0
        var vararg = false
        for (pattern in ast.patterns) {
            val value = if (pattern is RhovasAst.Pattern.VarargDestructure) {
                if (vararg) {
                    throw EvaluateException("Pattern cannot contain multiple varargs.")
                }
                vararg = true
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
        println("Visit Named: " + ast)
        if (patternState.value.type != Library.TYPES["Object"]) {
            return Object(Library.TYPES["Boolean"]!!, false)
        }
        val map = patternState.value.value as Map<String, Object>
        val named = ast.patterns.map { it.first }.toSet()
        var vararg = false
        for ((key, pattern) in ast.patterns) {
            val value = if (pattern is RhovasAst.Pattern.VarargDestructure) {
                if (vararg) {
                    throw EvaluateException("Pattern cannot contain multiple varargs.")
                }
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
                patternState.scope.variables.define(Variable(key, value.type, value))
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
        if (patternState.value.type == Library.TYPES["List"]) {
            val list = patternState.value.value as List<Object>
            if (ast.operator == "+" && list.isEmpty()) {
                return Object(Library.TYPES["Boolean"]!!, false)
            }
            return if (ast.pattern is RhovasAst.Pattern.Variable) {
                scope.variables.define(Variable(ast.pattern.name, Library.TYPES["List"]!!, Object(Library.TYPES["List"]!!, list)))
                Object(Library.TYPES["Boolean"]!!, true)
            } else {
                //TODO: Handle variable bindings
                Object(Library.TYPES["Boolean"]!!, list.all {
                    patternState = patternState.copy(value = it)
                    ast.pattern?.let { visit(it).value as Boolean } ?: true
                })
            }
        } else if (patternState.value.type == Library.TYPES["Object"]) {
            val map = patternState.value.value as Map<String, Object>
            if (ast.operator == "+" && map.isEmpty()) {
                return Object(Library.TYPES["Boolean"]!!, false)
            }
            return if (ast.pattern is RhovasAst.Pattern.Variable) {
                scope.variables.define(Variable(ast.pattern.name, Library.TYPES["Object"]!!, Object(Library.TYPES["Object"]!!, map)))
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
        val type = Library.TYPES[ast.name] ?: throw EvaluateException("Undefined type ${ast.name}.")
        return Object(Library.TYPES["Type"]!!, type)
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

    data class Break(val label: String?): Exception()

    data class Continue(val label: String?): Exception()

    data class Return(val value: Object?): Exception()

    data class Throw(val exception: Object): Exception()

    data class Lambda(
        val ast: RhovasAst.Expression.Lambda,
        val scope: Scope,
        val evaluator: Evaluator,
    ) {

        fun invoke(arguments: List<Triple<String, Type, Object>>, returns: Type): Object {
            //TODO: Lambda identification information for errors
            return evaluator.scoped(Scope(scope)) {
                if (ast.parameters.isNotEmpty()) {
                    if (ast.parameters.size != arguments.size) {
                        throw EvaluateException("Invalid parameter count for lambda: expected ${arguments.size}, received ${ast.parameters.size}")
                    }
                    val parameters = ast.parameters.map {
                        Pair(it.first, it.second?.let { evaluator.visit(it).value as Type } ?: Library.TYPES["Any"]!!)
                    }
                    for (i in parameters.indices) {
                        if (parameters[i].second.name != "Any" && parameters[i].second.name != arguments[i].second.name) {
                            throw EvaluateException("Invalid parameter type for lambda: expected ${arguments[i].second.name}, received ${parameters[i].second.name}.")
                        }
                        evaluator.scope.variables.define(Variable(parameters[i].first, parameters[i].second, arguments[i].third))
                    }
                } else if (arguments.size == 1) {
                    //TODO: entry name is (intentionally) unused
                    evaluator.scope.variables.define(Variable("val", arguments[0].second, arguments[0].third))
                } else {
                    evaluator.scope.variables.define(Variable("val", Library.TYPES["Object"]!!,
                        Object(Library.TYPES["Object"]!!, arguments.associate { it.first to it.third })))
                }
                try {
                    evaluator.visit(ast.body)
                    Object(Library.TYPES["Void"]!!, Unit)
                } catch (e: Return) {
                    if (e.value != null && returns.name != "Any" && e.value.type.name != returns.name) {
                        throw EvaluateException("Invalid return value from lambda: expected ${returns.name}, received ${e.value.type}.")
                    }
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
