package dev.rhovas.interpreter.parser.dsl

import dev.rhovas.interpreter.parser.Input
import dev.rhovas.interpreter.parser.Lexer
import dev.rhovas.interpreter.parser.Token

class DslLexer(input: Input) : Lexer<DslTokenType>(input) {

    override fun lexToken(): Token<DslTokenType>? {
        return when {
            chars[0] == null -> null
            peek("[\n\r]") -> lexIndent()
            peek("[\${}]") -> lexOperator()
            else -> lexText()
        }
    }

    /**
     * Lexes the indentation following a newline (used for the indentation-based
     * approach by the parser to avoid grammar restrictions).
     *
     *  - `indent = (?<= "\n" "\r"? | "\r" "\n"?) [ \t]*`
     */
    private fun lexIndent(): Token<DslTokenType> {
        require(match("[\n\r]"))
        match(if (chars[-1] == '\n') '\r' else '\n')
        chars.newline()
        while (match("[ \t]")) {}
        return chars.emit(DslTokenType.INDENT)
    }

    /**
     *  - `operator = [\${}]`
     */
    private fun lexOperator(): Token<DslTokenType> {
        require(match("[\${}]"))
        return chars.emit(DslTokenType.OPERATOR)
    }

    /**
     *  - `text := [^\n\r\${}]*`
     */
    private fun lexText(): Token<DslTokenType> {
        require(chars[0] != null)
        while (match("[^\n\r\${}]")) {}
        return chars.emit(DslTokenType.TEXT)
    }

}
