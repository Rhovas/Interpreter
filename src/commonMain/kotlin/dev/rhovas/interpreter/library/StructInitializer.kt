package dev.rhovas.interpreter.library

import dev.rhovas.interpreter.environment.Type

object StructInitializer : Library.TypeInitializer("Struct") {

    override fun initialize() {
        generics.add(generic("T", Type.STRUCT.DYNAMIC))
        inherits.add(Type.EQUATABLE[Type.STRUCT.DYNAMIC])
    }

}
