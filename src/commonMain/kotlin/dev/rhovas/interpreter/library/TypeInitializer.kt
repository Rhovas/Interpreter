package dev.rhovas.interpreter.library

import dev.rhovas.interpreter.environment.Component
import dev.rhovas.interpreter.environment.type.Type

object TypeInitializer : Library.ComponentInitializer(Component.Class("Type")) {

    override fun declare() {
        generics.add(generic("T"))
        inherits.add(Type.HASHABLE[Type.TYPE.DYNAMIC])
    }

    override fun define() {}

}
