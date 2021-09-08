package dev.rhovas.interpreter.parser.rhovas

import dev.rhovas.interpreter.parser.ParseException
import dev.rhovas.interpreter.parser.Parser

class RhovasParser(input: String) : Parser<RhovasTokenType>(RhovasLexer(input)) {

    override fun parse(): RhovasAst {
        TODO("source")
    }

    fun parseExpression(): RhovasAst.Expression {
        return parsePrimaryExpression()
    }

    private fun parsePrimaryExpression(): RhovasAst.Expression {
        return when {
            match("null") -> RhovasAst.Expression.Literal(null)
            match(listOf("true", "false")) -> RhovasAst.Expression.Literal(tokens[-1]!!.literal.toBooleanStrict())
            match(RhovasTokenType.INTEGER) -> RhovasAst.Expression.Literal(tokens[-1]!!.literal.toBigInteger())
            match(RhovasTokenType.DECIMAL) -> RhovasAst.Expression.Literal(tokens[-1]!!.literal.toBigDecimal())
            match(RhovasTokenType.STRING) -> {
                //TODO: Escapes
                RhovasAst.Expression.Literal(tokens[-1]!!.literal.removeSurrounding("\""))
            }
            match(":", RhovasTokenType.IDENTIFIER) -> RhovasAst.Expression.Literal(RhovasAst.Atom(tokens[-1]!!.literal))
            else -> throw ParseException("Expected expression.")
        }
    }

}
