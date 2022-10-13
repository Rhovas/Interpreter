package dev.rhovas.interpreter.parser.rhovas

import com.ionspin.kotlin.bignum.decimal.BigDecimal
import com.ionspin.kotlin.bignum.integer.BigInteger
import dev.rhovas.interpreter.parser.Input
import dev.rhovas.interpreter.parser.Lexer
import dev.rhovas.interpreter.parser.Token

class RhovasLexer(input: Input) : Lexer<RhovasTokenType>(input) {

    /**
     * Main lexer method, which dispatches to the correct `lex` method and
     * also skips comments/newlines.
     *
     *  - `comment = "//" [^\n\r]+`
     *  - `newline = "\n" "\r"? | "\r" "\n"?`
     */
    override fun lexToken(): Token<RhovasTokenType>? {
        if (mode == "string") {
            return lexStringMode()
        }
        while (match("/", "/") || match("[ \t\n\r]")) {
            when (chars[-1]) {
                '/' -> while (match("[^\n\r]")) {}
                '\n', '\r' -> {
                    match(if (chars[-1] == '\n') '\r' else '\n')
                    chars.newline()
                }
            }
        }
        chars.consume()
        return when {
            chars[0] == null -> null
            peek("[A-Za-z_]") -> lexIdentifier()
            peek("[0-9]") -> lexNumber()
            else -> lexOperator()
        }
    }

    /**
     *  - `identifier = [A-Za-z_][A-Za-z0-9_]*`
     */
    private fun lexIdentifier(): Token<RhovasTokenType> {
        require(match("[A-Za-z_]"))
        while (match("[A-Za-z0-9_]")) {}
        return chars.emit(RhovasTokenType.IDENTIFIER)
    }

    /**
     *  - `number = integer | decimal`
     *     - `integer = "0" ("b" [0-1] | "o" [0-7] | "x" [0-9A-F]) | [0-9]+`
     *     - `decimal = [0-9]+ "." [0-9]+ ("e" [0-9]+)`
     */
    private fun lexNumber(): Token<RhovasTokenType> {
        require(match("[0-9]"))
        if (chars[-1] == '0' && peek("[box]")) {
            fun lexBase(base: Int, digits: String): Token<RhovasTokenType> {
                while (match(digits)) {}
                return chars.emit(RhovasTokenType.INTEGER, BigInteger.parseString(chars.literal().substring(2), base))
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
            chars.emit(RhovasTokenType.DECIMAL, BigDecimal.parseString(chars.literal()))
        } else {
            chars.emit(RhovasTokenType.INTEGER, BigInteger.parseString(chars.literal()))
        }
    }

    /**
     *  - `operator = [^A-Za-z_0-9]`
     */
    private fun lexOperator(): Token<RhovasTokenType> {
        require(chars[0] != null)
        chars.advance()
        return chars.emit(RhovasTokenType.OPERATOR)
    }

    /**
     * Lexer mode for within string literals to support interpolation. Mode
     * switches and other validation occur in the parser
     *
     *  - `operator = [\"\n\r] | "${"`
     *  - `string = ([^\"\n\r\\] & (?! "${") | escape)*`
     *     - `escape = "\" ([nrt\"\$\\] | "u" [0-9A-F]{4})`
     */
    private fun lexStringMode(): Token<RhovasTokenType>? {
        return when {
            chars[0] == null -> null
            match("[\"\n\r]") || match('$', '{') -> chars.emit(RhovasTokenType.OPERATOR)
            else -> {
                val builder = StringBuilder()
                while (!peek('$', '{') && match("[^\"\n\r]")) {
                    if (chars[-1] == '\\') {
                        val start = chars.range.let {
                            it.copy(
                                index = it.index + it.length - 1,
                                column = it.column + it.length - 1,
                                length = 1
                            )
                        }
                        require(match("[nrtu\"\$\\\\]")) {
                            error(
                                "Invalid character escape.",
                                "A character escape is in the form \\char, where char is one of [nrtu\'\"\\]. If a literal backslash is desired, use an escape as in \"abc\\\\123\".",
                                start.copy(length = if (chars[0] != null) 2 else 1)
                            )
                        }
                        builder.append(
                            when (chars[-1]!!) {
                                'n' -> '\n'
                                'r' -> '\r'
                                't' -> '\t'
                                'u' -> Char((1..4).fold(0) { codepoint, index ->
                                    require(match("[0-9A-F]")) {
                                        error(
                                            "Invalid unicode escape.",
                                            "A unicode escape is in the form \\uXXXX, where X is a hexadecimal digit (one of [0-9A-F]). If a literal backslash is desired, use an escape as in \"abc\\\\123\".",
                                            start.copy(length = index + (if (chars[0] != null) 2 else 1))
                                        )
                                    }
                                    16 * codepoint + chars[-1]!!.digitToInt(16)
                                })
                                else -> chars[-1]!!
                            }
                        )
                    } else {
                        builder.append(chars[-1]!!)
                    }
                }
                chars.emit(RhovasTokenType.STRING, builder.toString())
            }
        }
    }

}
