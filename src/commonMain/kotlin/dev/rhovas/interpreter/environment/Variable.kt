package dev.rhovas.interpreter.environment

import dev.rhovas.interpreter.environment.type.Type

sealed interface Variable {

    val declaration: Declaration

    val name get() = declaration.name
    val type get() = declaration.type
    val mutable get() = declaration.mutable

    data class Declaration(
        override val name: String,
        override val type: Type,
        override val mutable: Boolean = false,
    ) : Variable {

        override val declaration = this

    }

    data class Definition(
        override val declaration: Declaration,
    ) : Variable {

        lateinit var value: Object

        constructor(declaration: Declaration, value: Object) : this(declaration) {
            this.value = value
        }

    }

}
