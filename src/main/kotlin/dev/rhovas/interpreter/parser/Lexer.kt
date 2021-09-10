package dev.rhovas.interpreter.parser

abstract class Lexer<T: Token.Type>(input: String) {

    protected val chars = CharStream(input)

    fun lex(): List<Token<T>> {
        return generateSequence { lexToken() }.toList()
    }

    abstract fun lexToken(): Token<T>?

    protected fun match(vararg objects: Any): Boolean {
        return peek(*objects).also {
            if (it) {
                repeat(objects.size) { chars.advance() }
            }
        }
    }

    protected fun peek(vararg objects: Any): Boolean {
        return objects.withIndex().all { o ->
            chars[o.index]?.let { test(o.value, it) } == true
        }
    }

    private fun test(obj: Any, char: Char): Boolean {
        return when(obj) {
            is Char -> obj == char
            is String -> char.toString().matches(obj.toRegex())
            is List<*> -> obj.any { test(it!!, char) }
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

    inner class CharStream(private val input: String) {

        private var index = 0
        private var length = 0

        operator fun get(offset: Int): Char? {
            return input.getOrNull(index + length + offset)
        }

        fun advance() {
            length++
        }

        fun consume() {
            index += length
            length = 0
        }

        fun literal(): String {
            return input.substring(index, index + length)
        }

        fun emit(type: T, value: Any? = null): Token<T> {
            return Token(type, literal(), value).also { consume() }
        }

    }

}
