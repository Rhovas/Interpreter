package dev.rhovas.interpreter.parser.dsl

import dev.rhovas.interpreter.parser.Token

enum class DslTokenType : Token.Type {
    INDENT,
    OPERATOR,
    TEXT,
}
