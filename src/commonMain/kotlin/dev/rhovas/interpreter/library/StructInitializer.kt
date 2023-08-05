package dev.rhovas.interpreter.library

import dev.rhovas.interpreter.environment.Modifiers
import dev.rhovas.interpreter.environment.Type

object StructInitializer : Library.TypeInitializer("Struct", Type.Component.CLASS, Modifiers(Modifiers.Inheritance.ABSTRACT)) {

    override fun initialize() {
        generics.add(generic("T", Type.STRUCT.DYNAMIC))
        inherits.add(Type.EQUATABLE[Type.STRUCT.DYNAMIC])
    }

}
