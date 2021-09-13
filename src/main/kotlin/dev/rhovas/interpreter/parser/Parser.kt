package dev.rhovas.interpreter.parser

abstract class Parser<T : Token.Type>(val lexer: Lexer<T>) {

    protected val tokens = TokenStream()

    abstract fun parse(rule: String): Any

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
        return require(condition) { "Broken parser invariant." }
    }

    protected fun require(condition: Boolean, error: () -> String) {
        if (!condition) {
            throw ParseException(error())
        }
    }

    inner class TokenStream {

        private val tokens = mutableListOf<Token<T>?>()
        private var index = 0

        operator fun get(offset: Int): Token<T>? {
            while (tokens.size <= index + offset) {
                tokens.add(lexer.lexToken())
            }
            return tokens[index + offset]
        }

        fun advance() {
            require(this[0] != null)
            index++
        }

    }

}
