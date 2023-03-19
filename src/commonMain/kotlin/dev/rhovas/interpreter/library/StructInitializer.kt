package dev.rhovas.interpreter.library

import dev.rhovas.interpreter.environment.Object
import dev.rhovas.interpreter.environment.Type

object StructInitializer : Library.TypeInitializer("Struct") {

    override fun initialize() {
        generics.add(generic("T", Type.STRUCT.ANY))
        inherits.add(Type.ANY)

        method("equals", operator = "==",
            parameters = listOf("other" to Type.STRUCT.ANY),
            returns = Type.BOOLEAN,
        ) { (instance, other) ->
            val instance = instance.value as Map<String, Object>
            val other = other.value as Map<String, Object>
            Object(Type.BOOLEAN, instance.keys == other.keys && instance.all { it.value.methods.equals(other[it.key]!!) })
        }

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
