package dev.rhovas.interpreter.parser

abstract class Lexer<T: Token.Type>(val input: Input) {

    protected val chars = CharStream()
    private val context = ArrayDeque<Input.Range>()

    var mode = ""
    var state
        get() = Pair(chars.range, context)
        set(value) {
            chars.range = value.first
            context.clear()
            context.addAll(value.second)
        }

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
            else -> throw AssertionError()
        }
    }

    protected fun require(condition: Boolean) {
        require(condition) { error("Broken lexer invariant.", """
            This is an internal compiler error, please report this!
            
            ${Exception().printStackTrace()}
        """.trimIndent()) }
    }

    protected fun require(condition: Boolean, error: () -> ParseException) {
        if (!condition) {
            throw error()
        }
    }

    protected fun error(message: String, details: String, range: Input.Range = chars.range): ParseException {
        return ParseException(message, details, range, listOf())
    }

    inner class CharStream {

        private var index = 0
        private var line = 1
        private var column = 0
        private var length = 0

        var range: Input.Range
            get() = Input.Range(index, line, column, length)
            set(value) {
                index = value.index
                line = value.line
                column = value.column
                length = value.length
            }

        operator fun get(offset: Int): Char? {
            return input.content.getOrNull(index + length + offset)
        }

        fun advance() {
            require(this[0] != null)
            length++
        }

        fun consume() {
            index += length
            column += length
            length = 0
        }

        fun newline() {
            require(this[-1] == '\n' || this[-1] == '\r')
            consume()
            line++
            column = 0
        }

        fun literal(): String {
            return input.content.substring(index, index + length)
        }

        fun emit(type: T, value: Any? = null): Token<T> {
            return Token(type, literal(), value, Input.Range(index, line, column, length)).also { consume() }
        }

    }

}
