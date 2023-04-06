package dev.rhovas.interpreter.library

import dev.rhovas.interpreter.environment.Object
import dev.rhovas.interpreter.environment.Type

object StructInitializer : Library.TypeInitializer("Struct") {

    override fun initialize() {
        generics.add(generic("T", Type.STRUCT.ANY))
        inherits.add(Type.EQUATABLE[Type.STRUCT.ANY])

        method("to",
            parameters = listOf("type" to Type.TYPE[Type.STRING]),
            returns = Type.STRING,
        ) { (instance) ->
            val type = instance.type
            val instance = instance.value as Map<String, Object>
            val name = type.base.name.takeIf { it != "Struct" } ?: ""
            val fields = instance.mapValues { it.value.methods.toString() }
            Object(Type.STRING, "${name}${fields}")
        }
    }

}
