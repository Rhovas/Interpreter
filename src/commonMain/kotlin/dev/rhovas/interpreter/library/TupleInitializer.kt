package dev.rhovas.interpreter.library

import dev.rhovas.interpreter.environment.Object
import dev.rhovas.interpreter.environment.Type

object TupleInitializer : Library.TypeInitializer("Tuple") {

    override fun initialize() {
        generics.add(generic("T", Type.TUPLE.ANY))
        inherits.add(Type.EQUATABLE[Type.TUPLE.ANY])

        method("to",
            parameters = listOf("type" to Type.TYPE[Type.STRING]),
            returns = Type.STRING,
        ) { (instance) ->
            val instance = instance.value as List<Object>
            Object(Type.STRING, instance.map { it.methods.toString() }.toString())
        }
    }

}
