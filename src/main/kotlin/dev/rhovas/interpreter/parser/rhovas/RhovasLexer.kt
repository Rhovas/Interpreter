package dev.rhovas.interpreter.parser.rhovas

import dev.rhovas.interpreter.parser.Lexer
import dev.rhovas.interpreter.parser.Token
import java.math.BigDecimal
import java.math.BigInteger

class RhovasLexer(input: String) : Lexer<RhovasTokenType>(input) {

    override fun lexToken(): Token<RhovasTokenType>? {
        while (match("[ \t\n\r]") || match("/", "/")) {
            if (chars[-1] == '/') {
                while (match("[^\n\r]")) {}
            }
        }
        chars.consume()
        return when {
            chars[0] == null -> null
            peek("[A-Za-z_]") -> lexIdentifier()
            peek("[0-9]") -> lexNumber()
            peek('"') -> lexString()
            else -> lexOperator()
        }
    }

    private fun lexIdentifier(): Token<RhovasTokenType> {
        require(match("[A-Za-z_]"))
        while (match("[A-Za-z0-9_]")) {}
        return chars.emit(RhovasTokenType.IDENTIFIER)
    }

    private fun lexNumber(): Token<RhovasTokenType> {
        require(match("[0-9]"))
        if (chars[-1] == '0' && peek("[box]")) {
            fun lexBase(base: Int, digits: String): Token<RhovasTokenType> {
                while (match(digits)) {}
                return chars.emit(RhovasTokenType.INTEGER, BigInteger(chars.literal().substring(2), base))
            }
            when {
                match('b', "[0-1]") -> return lexBase(2, "[0-1]")
                match('o', "[0-7]") -> return lexBase(8, "[0-7]")
                match('x', "[0-9A-F]") -> return lexBase(16, "[0-9A-F]")
            }
        }
        while (match("[0-9]")) {}
        return if (match('.', "[0-9]")) {
            while (match("[0-9]")) {}
            if (match("e", "[0-9]") || match("e", "[+\\-]", "[0-9]")) {
                while (match("[0-9]")) {}
            }
            chars.emit(RhovasTokenType.DECIMAL, BigDecimal(chars.literal()))
        } else {
            chars.emit(RhovasTokenType.INTEGER, BigInteger(chars.literal()))
        }
    }

    private fun lexString(): Token<RhovasTokenType> {
        require(match('"'))
        val builder = StringBuilder()
        while (match("[^\"\n\r]")) {
            if (chars[-1] == '\\') {
                require(match("[nrtu\"\$\\\\]")) { "Invalid character escape." }
                builder.append(when (chars[-1]!!) {
                    'n' -> '\n'
                    'r' -> '\r'
                    't' -> '\t'
                    'u' -> Char((1..4).fold(0) { codepoint, _ ->
                        require(match("[0-9A-F]")) { "Invalid unicode escape." }
                        16 * codepoint + chars[-1]!!.digitToInt(16)
                    })
                    else -> chars[-1]!!
                })
            } else {
                builder.append(chars[-1]!!)
            }
        }
        require(match('"')) { "Unterminated string literal." }
        return chars.emit(RhovasTokenType.STRING, builder.toString())
    }

    private fun lexOperator(): Token<RhovasTokenType> {
        require(chars[0] != null)
        chars.advance()
        //TODO: Multi-character operators?
        return chars.emit(RhovasTokenType.OPERATOR)
    }

}
