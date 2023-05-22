package dev.rhovas.interpreter.library

import dev.rhovas.interpreter.environment.Type

object TupleInitializer : Library.TypeInitializer("Tuple") {

    override fun initialize() {
        generics.add(generic("T", Type.TUPLE[Type.DYNAMIC]))
        inherits.add(Type.EQUATABLE[Type.TUPLE[Type.DYNAMIC]])
    }

}
