package dev.rhovas.interpreter.library

import dev.rhovas.interpreter.environment.Component
import dev.rhovas.interpreter.environment.Modifiers
import dev.rhovas.interpreter.environment.Object
import dev.rhovas.interpreter.environment.Type

object TupleInitializer : Library.ComponentInitializer(Component.Class("Tuple")) {

    override fun initialize() {
        generics.add(generic("T", Type.TUPLE.DYNAMIC))
        inherits.add(Type.EQUATABLE[Type.TUPLE.DYNAMIC])

        function("",
            generics = listOf(generic("T", Type.TUPLE.DYNAMIC)),
            parameters = listOf("initial" to generic("T", Type.TUPLE.DYNAMIC)),
            returns = generic("T", Type.TUPLE.DYNAMIC),
        ) { (initial) ->
            Object(initial.type, (initial.value as List<Object>).toMutableList())
        }
    }

}
