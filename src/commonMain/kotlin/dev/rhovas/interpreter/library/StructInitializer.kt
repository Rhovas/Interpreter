package dev.rhovas.interpreter.library

import dev.rhovas.interpreter.environment.Component
import dev.rhovas.interpreter.environment.Modifiers
import dev.rhovas.interpreter.environment.type.Type

object StructInitializer : Library.ComponentInitializer(Component.Class("Struct", Modifiers(Modifiers.Inheritance.VIRTUAL))) {

    override fun declare() {
        generics.add(generic("T", Type.STRUCT.DYNAMIC))
        inherits.add(Type.EQUATABLE[Type.STRUCT.DYNAMIC])
    }

    override fun define() {}

}
