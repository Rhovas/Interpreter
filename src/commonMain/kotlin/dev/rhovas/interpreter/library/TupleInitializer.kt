package dev.rhovas.interpreter.library

import dev.rhovas.interpreter.environment.Component
import dev.rhovas.interpreter.environment.Object
import dev.rhovas.interpreter.environment.type.Type

object TupleInitializer : Library.ComponentInitializer(Component.Class("Tuple")) {

    override fun declare() {
        generics.add(generic("T", Type.TUPLE.VARIANT)) // TODO
        inherits.add(Type.EQUATABLE[Type.TUPLE.VARIANT])
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
