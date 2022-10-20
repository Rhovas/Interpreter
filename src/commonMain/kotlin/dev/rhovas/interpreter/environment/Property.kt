package dev.rhovas.interpreter.environment

sealed interface Property {

    val getter: Method
    val setter: Method?

    val name get() = getter.name
    val type get() = getter.returns
    val mutable get() = setter != null

    data class Declaration(
        override val getter: Method,
        override val setter: Method?
    ) : Property

    data class Bound(
        override val getter: Method.Bound,
        override val setter: Method.Bound?,
    ) : Property

}
