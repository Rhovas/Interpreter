package dev.rhovas.interpreter.analyzer.rhovas

import dev.rhovas.interpreter.analyzer.Analyzer
import dev.rhovas.interpreter.environment.Method
import dev.rhovas.interpreter.environment.Scope
import dev.rhovas.interpreter.library.Library
import dev.rhovas.interpreter.parser.rhovas.RhovasAst
import java.lang.AssertionError
import java.math.BigDecimal
import java.math.BigInteger

class RhovasAnalyzer(scope: Scope) : Analyzer(scope), RhovasAst.Visitor<RhovasIr> {

    override fun visit(ast: RhovasAst.Source): RhovasIr {
        TODO()
    }

    private fun visit(ast: RhovasAst.Statement): RhovasIr.Statement {
        return super.visit(ast) as RhovasIr.Statement
    }

    override fun visit(ast: RhovasAst.Statement.Block): RhovasIr {
        TODO()
    }

    override fun visit(ast: RhovasAst.Statement.Expression): RhovasIr {
        TODO()
    }

    override fun visit(ast: RhovasAst.Statement.Function): RhovasIr {
        TODO()
    }

    override fun visit(ast: RhovasAst.Statement.Declaration): RhovasIr {
        TODO()
    }

    override fun visit(ast: RhovasAst.Statement.Assignment): RhovasIr {
        TODO()
    }

    override fun visit(ast: RhovasAst.Statement.If): RhovasIr {
        TODO()
    }

    override fun visit(ast: RhovasAst.Statement.Match.Conditional): RhovasIr {
        TODO()
    }

    override fun visit(ast: RhovasAst.Statement.Match.Structural): RhovasIr {
        TODO()
    }

    override fun visit(ast: RhovasAst.Statement.For): RhovasIr {
        TODO()
    }

    override fun visit(ast: RhovasAst.Statement.While): RhovasIr {
        TODO()
    }

    override fun visit(ast: RhovasAst.Statement.Try): RhovasIr {
        TODO()
    }

    override fun visit(ast: RhovasAst.Statement.Try.Catch): RhovasIr {
        TODO()
    }

    override fun visit(ast: RhovasAst.Statement.With): RhovasIr {
        TODO()
    }

    override fun visit(ast: RhovasAst.Statement.Label): RhovasIr {
        TODO()
    }

    override fun visit(ast: RhovasAst.Statement.Break): RhovasIr {
        TODO()
    }

    override fun visit(ast: RhovasAst.Statement.Continue): RhovasIr {
        TODO()
    }

    override fun visit(ast: RhovasAst.Statement.Return): RhovasIr {
        TODO()
    }

    override fun visit(ast: RhovasAst.Statement.Throw): RhovasIr {
        TODO()
    }

    override fun visit(ast: RhovasAst.Statement.Assert): RhovasIr {
        TODO()
    }

    override fun visit(ast: RhovasAst.Statement.Require): RhovasIr {
        TODO()
    }

    override fun visit(ast: RhovasAst.Statement.Ensure): RhovasIr {
        TODO()
    }

    private fun visit(ast: RhovasAst.Expression): RhovasIr.Expression {
        return super.visit(ast) as RhovasIr.Expression
    }

    override fun visit(ast: RhovasAst.Expression.Literal): RhovasIr.Expression.Literal {
        return when (ast.value) {
            is List<*> -> {
                val value = (ast.value as List<RhovasAst.Expression>).map { visit(it) }
                RhovasIr.Expression.Literal(value, Library.TYPES["List"]!!)
            }
            is Map<*, *> -> {
                val value = (ast.value as Map<String, RhovasAst.Expression>).mapValues { visit(it.value) }
                RhovasIr.Expression.Literal(value, Library.TYPES["Map"]!!)
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
        val method = expression.type.methods[ast.operator, 1]?.let { Method(it) } ?: throw error(
            ast,
            "Undefined method.",
            "The method ${expression.type.name}.${ast.operator}/0 is not defined.",
        )
        return RhovasIr.Expression.Unary(ast.operator, expression, method)
    }

    override fun visit(ast: RhovasAst.Expression.Binary): RhovasIr.Expression.Binary {
        val left = visit(ast.left)
        val method = left.type.methods[ast.operator, 2]?.let { Method(it) } ?: throw error(
            ast,
            "Undefined method.",
            "The method ${left.type.name}.${ast.operator}/1 is not defined.",
        )
        val right = visit(ast.right)
        require(right.type.isSubtypeOf(method.parameters[1])) { throw error(
            ast.right,
            "Invalid method argument type.",
            "The method ${left.type.name}.${ast.operator}/1 requires argument ${0} to be type ${method.parameters[1].name}, but received ${right.type.name}",
        ) }
        return RhovasIr.Expression.Binary(ast.operator, left, right, method)
    }

    override fun visit(ast: RhovasAst.Expression.Access.Variable): RhovasIr.Expression.Access.Variable {
        val variable = scope.variables[ast.name] ?: throw error(
            ast,
            "Undefined variable.",
            "The variable ${ast.name} is not defined in the current scope."
        )
        return RhovasIr.Expression.Access.Variable(variable)
    }

    override fun visit(ast: RhovasAst.Expression.Access.Property): RhovasIr.Expression.Access.Property {
        val receiver = visit(ast.receiver)
        val method = receiver.type.methods[ast.name, 1]?.let { Method(it) } ?: throw error(
            ast,
            "Undefined property.",
            "The property getter ${receiver.type.name}.${ast.name}/0 is not defined.",
        )
        //TODO: Coalesce typechecking (requires nullable types)
        return RhovasIr.Expression.Access.Property(receiver, method, ast.coalesce)
    }

    override fun visit(ast: RhovasAst.Expression.Access.Index): RhovasIr.Expression.Access.Index {
        val receiver = visit(ast.receiver)
        val method = receiver.type.methods["[]", 1 + ast.arguments.size]?.let { Method(it) } ?: throw error(
            ast,
            "Undefined method.",
            "The method ${receiver.type.name}.[]/${ast.arguments.size} is not defined.",
        )
        val arguments = ast.arguments.map { visit(it) }
        for (i in arguments.indices) {
            require(arguments[i].type.isSubtypeOf(method.parameters[i])) { throw error(
                ast.arguments[i],
                "Invalid method argument type.",
                "The method ${receiver.type.name}.[]=/${method.parameters.size} requires argument ${i} to be type ${method.parameters[i].name}, but received ${arguments[i].type.name}",
            ) }
        }
        return RhovasIr.Expression.Access.Index(receiver, method, arguments)
    }

    override fun visit(ast: RhovasAst.Expression.Invoke.Function): RhovasIr.Expression.Invoke.Function {
        val function = scope.functions[ast.name, ast.arguments.size] ?: throw error(
            ast,
            "Undefined function.",
            "The function ${ast.name}/${ast.arguments.size} is not defined in the current scope.",
        )
        val arguments = ast.arguments.map { visit(it) }
        for (i in arguments.indices) {
            require(arguments[i].type.isSubtypeOf(function.parameters[i])) { throw error(
                ast.arguments[i],
                "Invalid function argument type.",
                "The function ${function.name}/${function.parameters.size} requires argument ${i} to be type ${function.parameters[i].name}, but received ${arguments[i].type.name}",
            ) }
        }
        return RhovasIr.Expression.Invoke.Function(function, arguments)
    }

    override fun visit(ast: RhovasAst.Expression.Invoke.Method): RhovasIr.Expression.Invoke.Method {
        val receiver = visit(ast.receiver)
        val method = receiver.type.methods[ast.name, 1 + ast.arguments.size]?.let { Method(it) } ?: throw error(
            ast,
            "Undefined method.",
            "The method ${receiver.type.name}.${ast.name}/${ast.arguments.size} is not defined.",
        )
        val arguments = ast.arguments.map { visit(it) }
        for (i in arguments.indices) {
            require(arguments[i].type.isSubtypeOf(method.parameters[i])) { throw error(
                ast.arguments[i],
                "Invalid method argument type.",
                "The method ${receiver.type.name}.[]=/${method.parameters.size} requires argument ${i} to be type ${method.parameters[i].name}, but received ${arguments[i].type.name}",
            ) }
        }
        //TODO: Coalesce typechecking (requires nullable types)
        return RhovasIr.Expression.Invoke.Method(receiver, method, ast.coalesce, ast.cascade, arguments)
    }

    override fun visit(ast: RhovasAst.Expression.Invoke.Pipeline): RhovasIr.Expression.Invoke.Pipeline {
        val receiver = visit(ast.receiver)
        return if (ast.qualifier == null) {
            val function = scope.functions[ast.name, 1 + ast.arguments.size] ?: throw error(
                ast,
                "Undefined function.",
                "The function ${ast.name}/${1 + ast.arguments.size} is not defined in the current scope.",
            )
            val arguments = listOf(receiver) + ast.arguments.map { visit(it) }
            for (i in arguments.indices) {
                require(arguments[i].type.isSubtypeOf(function.parameters[i])) { throw error(
                    ast.arguments[i],
                    "Invalid function argument type.",
                    "The function ${function.name}/${function.parameters.size} requires argument ${i} to be type ${function.parameters[i].name}, but received ${arguments[i].type.name}",
                ) }
            }
            //TODO: Coalesce typechecking (requires nullable types)
            RhovasIr.Expression.Invoke.Pipeline(receiver, null, function, ast.coalesce, ast.cascade, arguments)
        } else {
            val qualifier = visit(ast.qualifier) as RhovasIr.Expression.Access
            //TODO: Static function scope
            val method = qualifier.type.methods[ast.name, 1 + 1 + ast.arguments.size]?.let { Method(it) } ?: throw error(
                ast,
                "Undefined method.",
                "The method ${qualifier.type.name}.${ast.name}/${1 + ast.arguments.size} is not defined.",
            )
            require(receiver.type.isSubtypeOf(method.parameters[0])) { error(
                ast,
                "Invalid method argument type.",
                "The method ${qualifier.type.name}.${ast.name}/${1 + ast.arguments.size} requires argument 0 to be type ${method.parameters[0].name}, but received ${receiver.type.name}",
            ) }
            val arguments = ast.arguments.map { visit(it) }
            for (i in arguments.indices) {
                require(arguments[i].type.isSubtypeOf(method.parameters[i + 1])) { throw error(
                    ast.arguments[i],
                    "Invalid method argument type.",
                    "The method ${qualifier.type.name}.${ast.name}/${1 + arguments.size} requires argument ${i} to be type ${method.parameters[i + 1].name}, but received ${arguments[i].type.name}",
                ) }
            }
            //TODO: Coalesce typechecking (requires nullable types)
            RhovasIr.Expression.Invoke.Pipeline(receiver, qualifier, method.function, ast.coalesce, ast.cascade, arguments)
        }
    }

    override fun visit(ast: RhovasAst.Expression.Lambda): RhovasIr.Expression.Lambda {
        //TODO: Validate using same requirements as function statements
        val parameters = ast.parameters.map { Pair(it.first, it.second?.let { visit(it) }) }
        val body = visit(ast.body)
        val type = Library.TYPES["Any"]!! //TODO: Requires type inference/unification
        return RhovasIr.Expression.Lambda(parameters, body, type)
    }

    override fun visit(ast: RhovasAst.Expression.Macro): RhovasIr.Expression.Macro {
        TODO()
    }

    override fun visit(ast: RhovasAst.Expression.Dsl): RhovasIr.Expression.Dsl {
        TODO()
    }

    override fun visit(ast: RhovasAst.Expression.Interpolation): RhovasIr.Expression.Interpolation {
        val expression = visit(ast.expression)
        return RhovasIr.Expression.Interpolation(expression)
    }

    override fun visit(ast: RhovasAst.Pattern.Variable): RhovasIr {
        TODO()
    }

    override fun visit(ast: RhovasAst.Pattern.Value): RhovasIr {
        TODO()
    }

    override fun visit(ast: RhovasAst.Pattern.Predicate): RhovasIr {
        TODO()
    }

    override fun visit(ast: RhovasAst.Pattern.OrderedDestructure): RhovasIr {
        TODO()
    }

    override fun visit(ast: RhovasAst.Pattern.NamedDestructure): RhovasIr {
        TODO()
    }

    override fun visit(ast: RhovasAst.Pattern.TypedDestructure): RhovasIr {
        TODO()
    }

    override fun visit(ast: RhovasAst.Pattern.VarargDestructure): RhovasIr {
        TODO()
    }

    override fun visit(ast: RhovasAst.Type): RhovasIr.Type {
        val type = Library.TYPES[ast.name] ?: throw error(
            ast,
            "Undefined type.",
            "The type ${ast.name} is not defined."
        )
        return RhovasIr.Type(type)
    }

}
