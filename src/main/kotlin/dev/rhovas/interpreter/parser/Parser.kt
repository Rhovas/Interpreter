package dev.rhovas.interpreter.parser

import java.util.*

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
        require(condition) { error("Broken parser invariant.", """
            This is an internal compiler error, please report this!
            
            ${Exception().printStackTrace()}
        """.trimIndent()) }
    }

    protected fun require(condition: Boolean, error: () -> ParseException) {
        if (!condition) {
            throw error()
        }
    }

    protected fun error(message: String, details: String, range: Input.Range = (tokens[0] ?: tokens[-1])!!.range): ParseException {
        return ParseException(message, details, range)
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
