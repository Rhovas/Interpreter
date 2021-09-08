package dev.rhovas.interpreter.parser.rhovas

import dev.rhovas.interpreter.parser.Lexer
import dev.rhovas.interpreter.parser.Token

class RhovasLexer(input: String) : Lexer<RhovasTokenType>(input) {

    override fun lexToken(): Token<RhovasTokenType>? {
        //TODO: Comments
        while (match("[ \t\n\r]")) {}
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
        //TODO: Binary, octal, hexadecimal
        while (match("[0-9]")) {}
        return if (match('.', "[0-9]")) {
            while (match("[0-9]")) {}
            //TODO: Scientific notation
            chars.emit(RhovasTokenType.DECIMAL)
        } else {
            chars.emit(RhovasTokenType.INTEGER)
        }
    }

    private fun lexString(): Token<RhovasTokenType> {
        require(match('"'))
        while (match("[^\"\n\r]")) {
            if (chars[-1] == '\\') {
                //TODO: Unicode escapes
                require(match("[nrt\"\$\\\\]")) { "Invalid character escape." }
            }
        }
        require(match('"')) { "Unterminated string literal." }
        return chars.emit(RhovasTokenType.STRING)
    }

    private fun lexOperator(): Token<RhovasTokenType> {
        require(chars[0] != null)
        chars.advance()
        //TODO: Multi-character operators?
        return chars.emit(RhovasTokenType.OPERATOR)
    }

}
