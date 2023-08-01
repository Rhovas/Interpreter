package dev.rhovas.interpreter.environment

data class Modifiers(
    val inheritance: Inheritance,
) {

    enum class Inheritance { DEFAULT, VIRTUAL, ABSTRACT }

}
