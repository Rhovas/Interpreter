package dev.rhovas.interpreter.parser.rhovas

import dev.rhovas.interpreter.parser.Token

enum class RhovasTokenType: Token.Type {
    IDENTIFIER,
    INTEGER,
    DECIMAL,
    STRING,
    ATOM,
    OPERATOR,
}
