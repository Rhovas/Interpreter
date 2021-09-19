package dev.rhovas.interpreter.evaluator

import dev.rhovas.interpreter.environment.Object
import dev.rhovas.interpreter.library.Library
import dev.rhovas.interpreter.parser.rhovas.RhovasAst
import java.math.BigDecimal
import java.math.BigInteger

class Evaluator : RhovasAst.Visitor<Object> {

    override fun visit(ast: RhovasAst.Statement.Block): Object {
        TODO()
    }

    override fun visit(ast: RhovasAst.Statement.Expression): Object {
        TODO()
    }

    override fun visit(ast: RhovasAst.Statement.Declaration): Object {
        TODO()
    }

    override fun visit(ast: RhovasAst.Statement.Assignment): Object {
        TODO()
    }

    override fun visit(ast: RhovasAst.Statement.If): Object {
        TODO()
    }

    override fun visit(ast: RhovasAst.Statement.Match): Object {
        TODO()
    }

    override fun visit(ast: RhovasAst.Statement.For): Object {
        TODO()
    }

    override fun visit(ast: RhovasAst.Statement.While): Object {
        TODO()
    }

    override fun visit(ast: RhovasAst.Statement.Try): Object {
        TODO()
    }

    override fun visit(ast: RhovasAst.Statement.With): Object {
        TODO()
    }

    override fun visit(ast: RhovasAst.Statement.Label): Object {
        TODO()
    }

    override fun visit(ast: RhovasAst.Statement.Break): Object {
        TODO()
    }

    override fun visit(ast: RhovasAst.Statement.Continue): Object {
        TODO()
    }

    override fun visit(ast: RhovasAst.Statement.Return): Object {
        TODO()
    }

    override fun visit(ast: RhovasAst.Statement.Throw): Object {
        TODO()
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
                Object(Library.TYPES["List"]!!, value)
            }
            is Map<*, *> -> {
                val value = (ast.value as Map<String, RhovasAst.Expression>).mapValues { visit(it.value) }
                Object(Library.TYPES["Object"]!!, value)
            }
            else -> throw AssertionError()
        }
    }

    override fun visit(ast: RhovasAst.Expression.Group): Object {
        TODO()
    }

    override fun visit(ast: RhovasAst.Expression.Unary): Object {
        TODO()
    }

    override fun visit(ast: RhovasAst.Expression.Binary): Object {
        TODO()
    }

    override fun visit(ast: RhovasAst.Expression.Access): Object {
        TODO()
    }

    override fun visit(ast: RhovasAst.Expression.Index): Object {
        TODO()
    }

    override fun visit(ast: RhovasAst.Expression.Function): Object {
        TODO()
    }

    override fun visit(ast: RhovasAst.Expression.Lambda): Object {
        TODO()
    }

    override fun visit(ast: RhovasAst.Expression.Macro): Object {
        TODO()
    }

    override fun visit(ast: RhovasAst.Expression.Dsl): Object {
        TODO()
    }

}
