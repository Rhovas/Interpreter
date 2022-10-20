package dev.rhovas.interpreter.parser

/**
 * Abstraction over the core structure for implementing a lexer, primarily:
 *
 *  - [lexToken]: Abstract method implementing the [Lexer]
 *  - [peek]/[match]: Helpers for checking/advancing the [CharStream]
 *  - [require]/[error]: Helpers for checking conditions and generating errors
 *  - [CharStream]: Manages input/tokenization state
 */
abstract class Lexer<T: Token.Type>(val input: Input) {

    protected val chars = CharStream()
    private val context = ArrayDeque<Input.Range>()

    /**
     * The current lexer mode (implementation-defined), commonly required for
     * contextual lexing between tokens as with string interpolation.
     */
    var mode = ""

    /**
     * The current lexer state, consisting of the input location from
     * [CharStream.range] and the active [context]. This state is publicly
     * mutable to allow sharing state between lexers, as required for DSLs.
     */
    var state
        get() = Pair(chars.range, context)
        set(value) {
            chars.range = value.first
            context.clear()
            context.addAll(value.second)
        }

    /**
     * Lexes and returns the next token, or `null` if the [CharStream] is empty.
     * Can be used with [generateSequence] to easily retrieve a token list.
     *
     * @throws ParseException
     */
    abstract fun lexToken(): Token<T>?

    /**
     * Returns `true` if the next sequence of characters match the given
     * arguments, which is either a [Char] or [String] regex.
     */
    protected fun peek(vararg objects: Any): Boolean {
        return objects.withIndex().all { o ->
            chars[o.index]?.let { test(o.value, it) } == true
        }
    }

    /**
     * Returns `true` in the same way as [peek], but also advances past the
     * matched characters if [peek] returns `true`.
     */
    protected fun match(vararg objects: Any): Boolean {
        return peek(*objects).also {
            if (it) {
                repeat(objects.size) { chars.advance() }
            }
        }
    }

    private fun test(obj: Any, char: Char): Boolean {
        return when(obj) {
            is Char -> obj == char
            is String -> char.toString().matches(obj.toRegex())
            else -> throw AssertionError()
        }
    }

    /**
     * Throws a default [ParseException] unless the given [condition] is `true`.
     * This method is intended for internal assertions, hence the message
     * `"Broken lexer invariant."`.
     */
    protected fun require(condition: Boolean) {
        require(condition) { error("Broken lexer invariant.", """
            This is an internal compiler error, please report this!
            
            ${Exception().printStackTrace()}
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
     * is the current [CharStream.range].
     */
    protected fun error(summary: String, details: String, range: Input.Range = chars.range): ParseException {
        return ParseException(summary, details, range, context)
    }

    /**
     * Manages the current state of the lexing/tokenization process for the
     * [Lexer].
     */
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

        /**
         * Returns the input character at the relative [offset] from the current
         * position, or `null` if the [CharStream] is empty. An offset of `0`
         * corresponds to the next, unconsumed character, while `-1` can be used
         * for the previous character.
         */
        operator fun get(offset: Int): Char? {
            return input.content.getOrNull(index + length + offset)
        }

        /**
         * Advances the [CharStream] past the next character, including that
         * character in the in-progress token.
         */
        fun advance() {
            require(this[0] != null)
            length++
        }

        /**
         * Consumes the in-progress token, starting a new token at the current
         * position.
         */
        fun consume() {
            index += length
            column += length
            length = 0
        }

        /**
         * Advances the [CharStream] to the next line, consuming the in-progress
         * token (if any). This method *must* be called by the implementing
         * [Lexer] to ensure accurate [line]/[column] values.
         */
        fun newline() {
            require(this[-1] == '\n' || this[-1] == '\r')
            consume()
            line++
            column = 0
        }

        /**
         * Returns the literal for the in-progress token.
         */
        fun literal(): String {
            return input.content.substring(index, index + length)
        }

        /**
         * Returns a new [Token], consuming the in-progress token.
         */
        fun emit(type: T, value: Any? = null): Token<T> {
            return Token(type, literal(), value, range).also { consume() }
        }

    }

}
