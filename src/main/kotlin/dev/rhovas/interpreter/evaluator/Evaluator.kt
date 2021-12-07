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
        require(ast.expression is RhovasAst.Expression.Invoke) { error(
            ast,
            "Invalid expression statement.",
            "An expression statement requires an invoke expression in order to perform a useful side-effect, but received ${ast.expression.javaClass.name}.",
        ) }
        visit(ast.expression)
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
                    require(arguments[i].type.isSubtypeOf(parameters[i].second))
                    scope.variables.define(Variable(parameters[i].first, parameters[i].second, arguments[i]))
                }
                try {
                    visit(ast.body)
                    require(Library.TYPES["Void"]!!.isSubtypeOf(returns)) { error(
                        ast,
                        "Missing return value.",
                        "The function ${ast.name}/${parameters.size} requires a return value, but received none."
                    ) }
                    Object(Library.TYPES["Void"]!!, Unit)
                } catch (e: Return) {
                    val value = e.value ?: Object(Library.TYPES["Void"]!!, Unit)
                    require(value.type.isSubtypeOf(returns)) { error(
                        e.ast, //TODO: Should be return statement AST
                        "Invalid return value.",
                        "The function ${ast.name}/${parameters.size} requires the return value to be type ${returns.name}, but received ${value.type.name}.",
                    ) }
                    value
                }
            }
        })
        return Object(Library.TYPES["Void"]!!, Unit)
    }

    override fun visit(ast: RhovasAst.Statement.Declaration): Object {
        //TODO: Enforce immutable variables
        require(ast.mutable || ast.value != null) { error(
            ast,
            "Uninitialized immutable variable.",
            "An immutable variable requires a value for initialization, as in `val name = value;`.",
        ) }
        //TODO: Redeclaration/shadowing
        val type = ast.type?.let { visit(it).value as Type } ?: Library.TYPES["Any"]!!
        val value = ast.value?.let { visit(it) } ?: Object(Library.TYPES["Null"]!!, null)
        //TODO: Proper handling of uninitialized variables
        require(ast.value == null || value.type.isSubtypeOf(type)) { error(
            ast,
            "Invalid variable value.",
            "The variable ${ast.name} requires the value to be type ${type.name}, but received ${value.type.name}.",
        ) }
        scope.variables.define(Variable(ast.name, type, value))
        return Object(Library.TYPES["Void"]!!, Unit)
    }

    override fun visit(ast: RhovasAst.Statement.Assignment): Object {
        require(ast.receiver is RhovasAst.Expression.Access) { error(
            ast,
            "Invalid assignment statement.",
            "An assignment statement requires the receiver to be an access expression (variable/property/index), but received ${ast.receiver.javaClass.name}.",
        ) }
        when (ast.receiver) {
            is RhovasAst.Expression.Access.Variable -> {
                val variable = scope.variables[ast.receiver.name] ?: throw error(
                    ast.receiver,
                    "Undefined variable.",
                    "The variable ${ast.receiver.name} is not defined in the current scope.",
                )
                //TODO: Variable types & mutability
                variable.set(visit(ast.value))
            }
            is RhovasAst.Expression.Access.Property -> {
                val receiver = visit(ast.receiver.receiver)
                val property = receiver.properties[ast.receiver.name] ?: throw error(
                    ast.receiver,
                    "Undefined property.",
                    "The property ${ast.receiver.name} is not defined by type ${receiver.type.name}.",
                )
                //TODO: Property mutability
                require(property.setter != null) {
                    error(
                        ast.receiver,
                        "Invalid property assignment.",
                        "The property ${ast.receiver.name} is not assignable.",
                    )
                }
                //TODO: Unify with invoke(...) helpers
                val value = visit(ast.value)
                require(value.type.isSubtypeOf(property.setter!!.parameters[1])) { error(
                    ast.value,
                    "Invalid function argument.",
                    "The property ${ast.receiver.name} requires the value to be type ${property.setter!!.parameters[1].name}, but received ${value.type.name}.",
                ) }
                trace("${receiver.type.name}.${ast.receiver.name}/1", ast.context.first()) {
                    property.set(value)
                }
            }
            is RhovasAst.Expression.Access.Index -> {
                val receiver = visit(ast.receiver.receiver)
                val method = receiver.methods["[]=", ast.receiver.arguments.size + 1] ?: throw error(
                    ast.receiver,
                    "Undefined index operator.",
                    "The operator []=/${ast.receiver.arguments.size + 1} (index assignment) is not defined by type ${receiver.type.name}.",
                )
                invoke(method, receiver, ast.receiver.arguments + listOf(ast.value), ast)
            }
        }
        return Object(Library.TYPES["Void"]!!, Unit)
    }

    override fun visit(ast: RhovasAst.Statement.If): Object {
        val condition = visit(ast.condition)
        require(condition.type.isSubtypeOf(Library.TYPES["Boolean"]!!)) { error(
            ast.condition,
            "Invalid if condition.",
            "An if statement requires the condition to be type Boolean, but received ${condition.type.name}.",
        ) }
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
            require(condition.type.isSubtypeOf(Library.TYPES["Boolean"]!!)) { error(
                it,
                "Invalid match case condition.",
                "A conditional match statement requires the condition to be type Boolean, but received ${condition.type.name}.",
            ) }
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
                "A structural match statements requires the patterns to be exhaustive, but no pattern matched argument ${argument.methods["toString", 0]!!.invoke(listOf()).value as String}.",
            )
        scoped(patternState.scope) {
            visit(case.second)
        }
        return Object(Library.TYPES["Void"]!!, Unit)
    }

    override fun visit(ast: RhovasAst.Statement.For): Object {
        val iterable = visit(ast.iterable)
        //TODO: Iterable type
        require(iterable.type.isSubtypeOf(Library.TYPES["List"]!!)) { error(
            ast.iterable,
            "Invalid for loop argument.",
            "A for loop requires the argument to be type List, but received ${iterable.type.name}.",
        ) }
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
            require(condition.type.isSubtypeOf(Library.TYPES["Boolean"]!!)) { error(
                ast.condition,
                "Invalid while loop condition.",
                "A while loop requires the condition to be type Boolean, but received ${condition.type.name}.",
            ) }
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
        require(ast.statement is RhovasAst.Statement.For || ast.statement is RhovasAst.Statement.While) { error(
            ast,
            "Invalid label statement.",
            "A label statement requires the statement to be a loop, but received ${ast.statement.javaClass.name}."
        ) }
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
        require(condition.type.isSubtypeOf(Library.TYPES["Boolean"]!!)) { error(
            ast.condition,
            "Invalid assert condition.",
            "An assert statement requires the condition to be type Boolean, but received ${condition.type.name}.",
        ) }
        require(condition.value as Boolean) { error(
            ast,
            "Failed assertion",
            "The assertion failed" + ast.message?.let { " (${visit(it).value})" } + ".",
        ) }
        return Object(Library.TYPES["Void"]!!, Unit)
    }

    override fun visit(ast: RhovasAst.Statement.Require): Object {
        val condition = visit(ast.condition)
        require(condition.type.isSubtypeOf(Library.TYPES["Boolean"]!!)) { error(
            ast.condition,
            "Invalid require condition.",
            "An require statement requires the condition to be type Boolean, but received ${condition.type.name}.",
        ) }
        require(condition.value as Boolean) { error(
            ast,
            "Failed precondition assertion.",
            "The precondition assertion failed" + ast.message?.let { " (${visit(it).value})" } + ".",
        ) }
        return Object(Library.TYPES["Void"]!!, Unit)
    }

    override fun visit(ast: RhovasAst.Statement.Ensure): Object {
        val condition = visit(ast.condition)
        require(condition.type.isSubtypeOf(Library.TYPES["Boolean"]!!)) { error(
            ast.condition,
            "Invalid ensure condition.",
            "An ensure statement requires the condition to be type Boolean, but received ${condition.type.name}.",
        ) }
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
        val method = expression.methods[ast.operator, 0] ?: throw error(
            ast,
            "Undefined unary operator.",
            "The operator ${ast.operator}/0 is not defined by type ${expression.type.name}.",
        )
        return method.invoke(listOf())
    }

    override fun visit(ast: RhovasAst.Expression.Binary): Object {
        val left = visit(ast.left)
        return when (ast.operator) {
            "||" -> {
                require(left.type.isSubtypeOf(Library.TYPES["Boolean"]!!)) { error(
                    ast.left,
                    "Invalid binary operand.",
                    "The binary operator || (logical or) requires the left operand to be type Boolean, but received ${left.type.name}.",
                ) }
                if (left.value as Boolean) {
                    Object(Library.TYPES["Boolean"]!!, true)
                } else {
                    val right = visit(ast.right)
                    require(right.type.isSubtypeOf(Library.TYPES["Boolean"]!!)) { error(
                        ast.right,
                        "Invalid binary operand.",
                        "The binary operator || (logical or) requires the right operand to be type Boolean, but received ${right.type.name}.",
                    ) }
                    Object(Library.TYPES["Boolean"]!!, right.value as Boolean)
                }
            }
            "&&" -> {
                require(left.type.isSubtypeOf(Library.TYPES["Boolean"]!!)) { error(
                    ast.left,
                    "Invalid binary operand.",
                    "The binary operator && (logical and) requires the left operand to be type Boolean, but received ${left.type.name}.",
                ) }
                if (left.value as Boolean) {
                    val right = visit(ast.right)
                    require(right.type.isSubtypeOf(Library.TYPES["Boolean"]!!)) { error(
                        ast.right,
                        "Invalid binary operand.",
                        "The binary operator && (logical and) requires the right operand to be type Boolean, but received ${right.type.name}.",
                    ) }
                    Object(Library.TYPES["Boolean"]!!, right.value as Boolean)
                } else {
                    Object(Library.TYPES["Boolean"]!!, false)
                }
            }
            "==", "!=" -> {
                val method = left.methods["==", 1] ?: throw error(
                    ast,
                    "Undefined binary operator.",
                    "The binary operator == (equals) is not defined by type ${left.type.name}.",
                )
                val right = visit(ast.right)
                val result = if (right.type.isSubtypeOf(method.function.parameters[0])) {
                    method.invoke(listOf(right)).value as Boolean
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
                val method = left.methods["<=>", 1] ?: throw error(
                    ast,
                    "Undefined binary operator.",
                    "The binary operator ${left.type.name}.<=> (compare) is not defined.",
                )
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
                val method = left.methods[ast.operator, 1] ?: throw error(
                    ast,
                    "Undefined binary operator.",
                    "The binary operator ${ast.operator} is not defined by type ${left.type.name}.",
                )
                invoke(method, left, listOf(ast.right), ast)
            }
            else -> throw AssertionError()
        }
    }

    override fun visit(ast: RhovasAst.Expression.Access.Variable): Object {
        val variable = scope.variables[ast.name] ?: throw error(
            ast,
            "Undefined variable.",
            "The variable ${ast.name} is not defined in the current scope.",
        )
        return variable.get()
    }

    override fun visit(ast: RhovasAst.Expression.Access.Property): Object {
        val receiver = visit(ast.receiver)
        return if (ast.coalesce && receiver.type.isSubtypeOf(Library.TYPES["Null"]!!)) {
            receiver
        } else {
            val property = receiver.properties[ast.name] ?: throw error(
                ast,
                "Undefined property.",
                "The property ${ast.name} is not defined by type ${receiver.type.name}.",
            )
            property.get()
        }
    }

    override fun visit(ast: RhovasAst.Expression.Access.Index): Object {
        val receiver = visit(ast.receiver)
        val method = receiver.methods["[]", ast.arguments.size] ?: throw error(
            ast,
            "Undefined index operator.",
            "The operator []/${ast.arguments.size} (index access) is not defined by type ${receiver.type.name}.",
        )
        return invoke(method, receiver, ast.arguments, ast)
    }

    override fun visit(ast: RhovasAst.Expression.Invoke.Function): Object {
        val function = scope.functions[ast.name, ast.arguments.size] ?: throw error(
            ast,
            "Undefined function.",
            "The function ${ast.name}/${ast.arguments.size} is not defined in the current scope.",
        )
        return invoke(function, ast.arguments, ast)
    }

    override fun visit(ast: RhovasAst.Expression.Invoke.Method): Object {
        val receiver = visit(ast.receiver)
        return if (ast.coalesce && receiver.type.isSubtypeOf(Library.TYPES["Null"]!!)) {
            Object(Library.TYPES["Null"]!!, null)
        } else {
            val method = receiver.methods[ast.name, ast.arguments.size] ?: throw error(
                ast,
                "Undefined method.",
                "The method ${ast.name}/${ast.arguments.size} is not defined by type ${receiver.type.name}.",
            )
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
                val function = scope.functions[ast.name, ast.arguments.size + 1] ?: throw error(
                    ast,
                    "Undefined function.",
                    "The function ${ast.name}/${ast.arguments.size + 1} is not defined in the current scope.",
                )
                //TODO: Unify with invoke(...) helper
                val arguments = listOf(receiver) + ast.arguments.map { visit(it) }
                for (i in arguments.indices) {
                    require(arguments[i].type.isSubtypeOf(function.parameters[i])) { error(
                        ast.arguments[i],
                        "Invalid function argument.",
                        "The function ${function.name}/${function.parameters.size} requires argument ${i} to be type ${function.parameters[i].name}, but received ${arguments[i].type.name}.",
                    ) }
                }
                trace("Source.${function.name}/${arguments.size}", ast.context.first()) {
                    function.invoke(arguments)
                }
            } else {
                val qualifier = visit(ast.qualifier)
                val method = qualifier.methods[ast.name, ast.arguments.size + 1] ?: throw error(
                    ast,
                    "Undefined method.",
                    "The method ${ast.name}/${ast.arguments.size + 1} is not defined by type ${qualifier.type.name}.",
                )
                //TODO: Unify with invoke(...) helper
                val arguments = listOf(receiver) + ast.arguments.map { visit(it) }
                for (i in arguments.indices) {
                    require(arguments[i].type.isSubtypeOf(method.function.parameters[i + 1])) { error(
                        ast.arguments[i],
                        "Invalid method argument.",
                        "The method ${method.function.name}/${arguments.size} requires argument ${i} to be type ${method.function.parameters[i + 1].name}, but received ${arguments[i].type.name}.",
                    ) }
                }
                trace("${qualifier.type.name}.${method.function.name}/${arguments.size}", ast.context.first()) {
                    method.invoke(arguments)
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

    override fun visit(ast: RhovasAst.Pattern.Variable): Object {
        if (ast.name != "_") {
            patternState.scope.variables.define(Variable(ast.name, patternState.value.type, patternState.value))
        }
        return Object(Library.TYPES["Boolean"]!!, true)
    }

    override fun visit(ast: RhovasAst.Pattern.Value): Object {
        val value = visit(ast.value)
        val method = value.methods["==", 1] ?: throw error(
            ast,
            "Undefined binary operator.",
            "The operator ==/1 (equals) is not defined by type ${value.type.name}.",
        )
        val result = if (value.type == patternState.value.type) method.invoke(listOf(patternState.value)).value as Boolean else false
        return Object(Library.TYPES["Boolean"]!!, result)
    }

    override fun visit(ast: RhovasAst.Pattern.Predicate): Object {
        var result = visit(ast.pattern)
        if (result.value as Boolean) {
            result = visit(ast.predicate)
            require(result.type.isSubtypeOf(Library.TYPES["Boolean"]!!)) { error(
                ast,
                "Invalid predicate pattern.",
                "A predicate pattern requires the predicate to be type Boolean, but received ${result.type.name}.",
            ) }
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
                require(!vararg) { error(
                    pattern,
                    "Multiple vararg patterns",
                    "An ordered destructure requires no more than one vararg pattern.",
                ) }
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
        if (patternState.value.type != Library.TYPES["Object"]) {
            return Object(Library.TYPES["Boolean"]!!, false)
        }
        val map = patternState.value.value as Map<String, Object>
        val named = ast.patterns.map { it.first }.toSet()
        var vararg = false
        for ((key, pattern) in ast.patterns) {
            val value = if (pattern is RhovasAst.Pattern.VarargDestructure) {
                require(!vararg) { error(
                    pattern,
                    "Multiple vararg patterns",
                    "An ordered destructure requires no more than one vararg pattern.",
                ) }
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
        if (patternState.value.type.isSubtypeOf(Library.TYPES["List"]!!)) {
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
        } else if (patternState.value.type.isSubtypeOf(Library.TYPES["Object"]!!)) {
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
        val type = Library.TYPES[ast.name] ?: throw error(
            ast,
            "Undefined type.",
            "The type ${ast.name} is not defined."
        )
        return Object(Library.TYPES["Type"]!!, type)
    }

    private fun invoke(function: Function, arguments: List<RhovasAst.Expression>, ast: RhovasAst): Object {
        //TODO: Evaluation/typechecking order
        val evaluated = arguments.map { visit(it) }
        for (i in evaluated.indices) {
            require(evaluated[i].type.isSubtypeOf(function.parameters[i])) { error(
                arguments[i],
                "Invalid function argument.",
                "The function ${function.name}/${evaluated.size} requires argument ${i} to be type ${function.parameters[i].name}, but received ${evaluated[i].type.name}.",
            ) }
        }
        //TODO: Function namespaces
        return trace("Source.${function.name}/${evaluated.size}", ast.context.first()) {
            function.invoke(evaluated)
        }
    }

    private fun invoke(method: Method, receiver: Object, arguments: List<RhovasAst.Expression>, ast: RhovasAst): Object {
        val evaluated = arguments.map { visit(it) }
        for (i in evaluated.indices) {
            require(evaluated[i].type.isSubtypeOf(method.function.parameters[i + 1])) { error(
                arguments[i],
                "Invalid method argument.",
                "The method ${receiver.type.name}.${method.function.name}/${evaluated.size} requires argument ${i} to be type ${method.function.parameters[i + 1].name}, but received ${evaluated[i].type.name}.",
            ) }
        }
        //TODO: Method namespaces (for inheritance)
        return trace("${receiver.type.name}.${method.function.name}/${evaluated.size}", ast.context.first()) {
            method.invoke(evaluated)
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
                    require(ast.parameters.size == arguments.size) { evaluator.error(
                        ast,
                        "Invalid lambda parameter count.",
                        "Expected ${arguments.size}, but received ${ast.parameters.size}.",
                    ) }
                    val parameters = ast.parameters.map {
                        Pair(it.first, it.second?.let { evaluator.visit(it).value as Type } ?: Library.TYPES["Any"]!!)
                    }
                    for (i in parameters.indices) {
                        require(arguments[i].second.isSubtypeOf(parameters[i].second)) { evaluator.error(
                            ast,
                            "Invalid lambda argument type.",
                            "Expected argument ${i} to be type ${parameters[i].second.name}, but received ${arguments[i].second.name}.",
                        ) }
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
                    val value = e.value ?: Object(Library.TYPES["Void"]!!, Unit)
                    require(value.type.isSubtypeOf(returns)) { evaluator.error(
                        ast,
                        "Invalid lambda return value.",
                        "Expected the return value to be type ${returns.name}, but received ${value.type.name}.",
                    ) }
                    value
                }
            }
        }

    }

    data class PatternState(
        val scope: Scope,
        val value: Object,
    )

}
