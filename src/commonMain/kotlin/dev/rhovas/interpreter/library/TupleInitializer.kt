package dev.rhovas.interpreter.library

import dev.rhovas.interpreter.environment.Component
import dev.rhovas.interpreter.environment.Modifiers
import dev.rhovas.interpreter.environment.Object
import dev.rhovas.interpreter.environment.Type

object TupleInitializer : Library.ComponentInitializer(Component.Class("Tuple")) {

    override fun declare() {
        generics.add(generic("T", Type.TUPLE.DYNAMIC))
        inherits.add(Type.EQUATABLE[Type.TUPLE.DYNAMIC])
    }

    override fun define() {
        function("",
            generics = listOf(generic("T", Type.TUPLE.DYNAMIC)),
            parameters = listOf("initial" to generic("T", Type.TUPLE.DYNAMIC)),
            returns = generic("T", Type.TUPLE.DYNAMIC),
        ) { (initial): T1<List<Object>> ->
            Object(generics["T"]!!, initial.toMutableList())
        }
    }

}
