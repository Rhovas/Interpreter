package dev.rhovas.interpreter.library

import dev.rhovas.interpreter.environment.Type

object TypeInitializer : Library.TypeInitializer("Type", Type.Component.CLASS) {

    override fun initialize() {
        generics.add(generic("T"))
        inherits.add(Type.HASHABLE[Type.TYPE.DYNAMIC])
    }

}
