package dev.rhovas.interpreter.library

import dev.rhovas.interpreter.environment.Component
import dev.rhovas.interpreter.environment.Modifiers
import dev.rhovas.interpreter.environment.Scope
import dev.rhovas.interpreter.environment.Type

object TypeInitializer : Library.ComponentInitializer(Component.Class("Type", Modifiers(Modifiers.Inheritance.DEFAULT), Scope.Definition(null))) {

    override fun initialize() {
        generics.add(generic("T"))
        inherits.add(Type.HASHABLE[Type.TYPE.DYNAMIC])
    }

}
