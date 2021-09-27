package dev.rhovas.interpreter.evaluator

import dev.rhovas.interpreter.environment.Object
import dev.rhovas.interpreter.environment.Scope
import dev.rhovas.interpreter.environment.Variable
import dev.rhovas.interpreter.library.Library
import dev.rhovas.interpreter.parser.rhovas.RhovasAst
import java.math.BigDecimal
import java.math.BigInteger
import java.util.function.Predicate

class Evaluator(private var scope: Scope) : RhovasAst.Visitor<Object> {

    private var label: String? = null

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

    override fun visit(ast: RhovasAst.Statement.Declaration): Object {
        //TODO: Immutable variables
        if (!ast.mutable && ast.value == null) {
            //TODO: Semantic analysis validation
            throw EvaluateException("Immutable variable requires a value.")
        }
        //TODO: Redeclaration/shadowing
        scope.variables.define(Variable(ast.name, ast.value?.let { visit(it) }
            ?: Object(Library.TYPES["Null"]!!, null)))
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

    override fun visit(ast: RhovasAst.Statement.Match): Object {
        val predicate = if (ast.argument != null) {
            val argument = visit(ast.argument)
            val method = argument.methods["==", 1]
                ?: throw EvaluateException("Match argument is not supported by type ${argument.type.name}.")
            Predicate { value: Object ->
                if (value.type != argument.type) {
                    throw EvaluateException("Match case value is not supported by type ${value.type.name} with argument ${argument.type.name}.")
                }
                method.invoke(listOf(value)).value as Boolean
            }
        } else {
            Predicate { condition: Object ->
                if (condition.type != Library.TYPES["Boolean"]!!) {
                    throw EvaluateException("Match case condition is not supported by type ${condition.type.name}.")
                }
                condition.value as Boolean
            }
        }
        val case = ast.cases.firstOrNull { predicate.test(visit(it.first)) }
            ?: ast.elseCase?.also {
                if (it.first?.let { predicate.test(visit(it)) } == false) {
                    throw EvaluateException("Match else condition returned false.")
                }
            }
            ?: if (ast.argument != null) {
                throw EvaluateException("Structural match requires exhaustive cases.")
            } else null
        case?.let { visit(it.second) }
        return Object(Library.TYPES["Void"]!!, Unit)
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
                    scope.variables.define(Variable(ast.name, element))
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
                    scope.variables.define(Variable(it.name, e.exception))
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

        fun invoke(arguments: Map<String, Object>): Object {
            return evaluator.scoped(Scope(scope)) {
                if (ast.parameters.isNotEmpty()) {
                    ast.parameters.zip(arguments.values)
                        .forEach { evaluator.scope.variables.define(Variable(it.first, it.second)) }
                } else if (arguments.size == 1) {
                    //TODO: entry name is (intentionally) unused
                    evaluator.scope.variables.define(Variable("val", arguments.values.first()))
                } else {
                    evaluator.scope.variables.define(Variable("val", Object(Library.TYPES["Object"]!!, arguments.toMutableMap())))
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

}
