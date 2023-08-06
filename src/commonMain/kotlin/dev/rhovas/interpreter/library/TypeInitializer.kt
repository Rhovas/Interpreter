package dev.rhovas.interpreter.library

import dev.rhovas.interpreter.environment.Component
import dev.rhovas.interpreter.environment.Type

object TypeInitializer : Library.ComponentInitializer(Component.Class("Type")) {

    override fun initialize() {
        generics.add(generic("T"))
        inherits.add(Type.HASHABLE[Type.TYPE.DYNAMIC])
    }

}
