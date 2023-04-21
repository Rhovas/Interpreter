package dev.rhovas.interpreter.parser

/**
 * Abstraction over the core structure for implementing a lexer, primarily:
 *
 *  - [parse]: Abstract method implementing the [Parser]
 *  - [peek]/[match]: Helpers for checking/advancing the [TokenStream]
 *  - [require]/[error]: Helpers for checking conditions and generating errors
 *  - [TokenStream]: Manages lexer/token state
 */
abstract class Parser<T : Token.Type>(val lexer: Lexer<T>) {

    protected val tokens = TokenStream()
    protected val context = lexer.state.second

    /**
     * Parses and returns the AST corresponding to the given [rule]
     * (implementation-defined). All parsers should support a `"source"` rule
     * that acts as the entry-point for the language.
     *
     * @throws ParseException
     */
    abstract fun parse(rule: String): Any

    /**
     * Returns `true` if the next sequence of characters match the given
     * arguments, which is either a [Token.Type], [String] literal, or [List] of
     * potential patterns.
     */
    protected fun peek(vararg objects: Any): Boolean {
        return objects.withIndex().all { o ->
            tokens[o.index]?.let { test(o.value, it) } == true
        }
    }

    /**
     * Returns `true` in the same way as [peek], but also advances past the
     * matched tokens if [peek] returns `true`.
     */
    protected fun match(vararg objects: Any): Boolean {
        return peek(*objects).also {
            if (it) {
                repeat(objects.size) { tokens.advance() }
            }
        }
    }

    private fun test(obj: Any, token: Token<T>): Boolean {
        return when(obj) {
            is Token.Type -> obj == token.type
            is String -> obj == token.literal
            is List<*> -> obj.any { test(it!!, token) }
            else -> throw AssertionError()
        }
    }

    /**
     * Throws a default [ParseException] unless the given [condition] is `true`.
     * This method is intended for internal assertions, hence the message
     * `"Broken parser invariant."`.
     */
    protected fun require(condition: Boolean) {
        require(condition) { error("Broken parser invariant.", """
            This is an internal compiler error, please report this!
            
            ${Exception().stackTraceToString()}
        """.trimIndent()) }
    }

    /**
     * Throws a custom [ParseException] unless the given [condition] is `true`.
     */
    protected fun require(condition: Boolean, error: () -> ParseException) {
        if (!condition) {
            throw error()
        }
    }

    /**
     * Returns a [ParseException] with the given arguments. The default [range]
     * is the range of the next token or, if `null`, the previous token.
     */
    protected fun error(summary: String, details: String, range: Input.Range = (tokens[0] ?: tokens[-1])!!.range): ParseException {
        return ParseException(summary, details, range, context + listOfNotNull(tokens[0]?.range, tokens[-1]?.range))
    }

    /**
     * Manages the current state of the lexing/tokenization process for the
     * [Parser].
     */
    inner class TokenStream {

        private val tokens = mutableListOf<Token<T>?>()
        private var index = 0

        /**
         * Returns the [Token] at the relative [offset] from the current
         * position, or `null` if the [TokenStream] is empty. An offset of `0`
         * corresponds to the next, unconsumed token, while `-1` can be used
         * for the previous token.
         */
        operator fun get(offset: Int): Token<T>? {
            while (tokens.size <= index + offset) {
                tokens.add(lexer.lexToken())
            }
            return tokens.getOrNull(index + offset)
        }

        /**
         * Advances the [TokenStream] past the next token.
         */
        fun advance() {
            require(this[0] != null)
            index++
        }

    }

}
