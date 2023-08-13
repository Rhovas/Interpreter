package dev.rhovas.interpreter.environment

data class Modifiers(
    val inheritance: Inheritance = Inheritance.FINAL,
    val override: Boolean = false,
) {

    enum class Inheritance { FINAL, VIRTUAL, ABSTRACT }

}
