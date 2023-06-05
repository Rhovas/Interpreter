package dev.rhovas.interpreter.library

import dev.rhovas.interpreter.environment.Object
import dev.rhovas.interpreter.environment.Type

object TupleInitializer : Library.TypeInitializer("Tuple") {

    override fun initialize() {
        generics.add(generic("T", Type.TUPLE[Type.DYNAMIC]))
        inherits.add(Type.EQUATABLE[Type.TUPLE[Type.DYNAMIC]])

        function("",
            generics = listOf(generic("T", Type.TUPLE[Type.DYNAMIC])),
            parameters = listOf("initial" to generic("T", Type.TUPLE[Type.DYNAMIC])),
            returns = generic("T", Type.TUPLE[Type.DYNAMIC]),
        ) { (initial) ->
            Object(initial.type, (initial.value as List<Object>).toMutableList())
        }
    }

}
