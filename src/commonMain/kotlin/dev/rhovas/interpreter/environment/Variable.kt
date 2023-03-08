package dev.rhovas.interpreter.environment

sealed interface Variable {

    val name: String
    val type: Type
    val mutable: Boolean

    data class Declaration(
        override val name: String,
        override val type: Type,
        override val mutable: Boolean,
    ) : Variable

    data class Definition(
        val declaration: Declaration,
    ) : Variable by declaration {

        lateinit var value: Object

        constructor(declaration: Declaration, value: Object) : this(declaration) {
            this.value = value
        }

    }

}
