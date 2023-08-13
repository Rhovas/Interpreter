package dev.rhovas.interpreter.environment

data class Modifiers(
    val inheritance: Inheritance,
    val override: Boolean = false,
) {

    enum class Inheritance { DEFAULT, VIRTUAL, ABSTRACT }

}
