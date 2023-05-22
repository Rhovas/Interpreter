package dev.rhovas.interpreter.library

import dev.rhovas.interpreter.environment.Type

object TypeInitializer : Library.TypeInitializer("Type") {

    override fun initialize() {
        generics.add(generic("T"))
        inherits.add(Type.HASHABLE[Type.TYPE[Type.DYNAMIC]])
    }

}
