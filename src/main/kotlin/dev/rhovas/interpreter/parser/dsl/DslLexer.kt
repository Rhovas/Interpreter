package dev.rhovas.interpreter.parser.dsl

import dev.rhovas.interpreter.parser.Lexer
import dev.rhovas.interpreter.parser.Token

class DslLexer(input: String) : Lexer<DslTokenType>(input) {

    override fun lexToken(): Token<DslTokenType>? {
        return when {
            chars[0] == null -> null
            peek("[\n\r]") -> lexIndent()
            peek("[\${}]") -> lexOperator()
            else -> lexText()
        }
    }

    private fun lexIndent(): Token<DslTokenType> {
        require(match("[\n\r]"))
        match(if (chars[-1] == '\n') '\r' else '\n')
        chars.consume()
        while (match("[ \t]")) {}
        return chars.emit(DslTokenType.INDENT)
    }

    private fun lexOperator(): Token<DslTokenType> {
        require(match("[\${}]"))
        return chars.emit(DslTokenType.OPERATOR)
    }

    private fun lexText(): Token<DslTokenType> {
        require(chars[0] != null)
        while (match("[^\n\r\${}]")) {}
        return chars.emit(DslTokenType.TEXT)
    }

}
