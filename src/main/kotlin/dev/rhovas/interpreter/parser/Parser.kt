package dev.rhovas.interpreter.parser

abstract class Parser<T : Token.Type>(lexer: Lexer<T>) {

    protected val tokens = TokenStream(lexer.lex())

    abstract fun parse(): Any

    protected fun match(vararg objects: Any): Boolean {
        return peek(*objects).also {
            if (it) {
                repeat(objects.size) { tokens.advance() }
            }
        }
    }

    protected fun peek(vararg objects: Any): Boolean {
        return objects.withIndex().all { o ->
            tokens[o.index]?.let { test(o.value, it) } == true
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

    protected fun require(condition: Boolean) {
        return require(condition) { "Broken lexer invariant." }
    }

    protected fun require(condition: Boolean, error: () -> String) {
        if (!condition) {
            throw ParseException(error())
        }
    }

    inner class TokenStream(private val tokens: List<Token<T>>) {

        private var index = 0

        operator fun get(offset: Int): Token<T>? {
            return tokens.getOrNull(index + offset)
        }

        fun advance() {
            index++
        }

    }

}
