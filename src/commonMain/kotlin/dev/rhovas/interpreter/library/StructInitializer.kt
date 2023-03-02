package dev.rhovas.interpreter.library

import dev.rhovas.interpreter.EVALUATOR
import dev.rhovas.interpreter.environment.Object
import dev.rhovas.interpreter.environment.Type
import dev.rhovas.interpreter.parser.rhovas.RhovasAst

object StructInitializer : Library.TypeInitializer("Struct") {

    override fun initialize() {
        generics.add(generic("T", Type.STRUCT.ANY))
        inherits.add(Type.ANY)

        method("equals", operator = "==",
            parameters = listOf("other" to Type.OBJECT),
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
            Object(Type.STRING, type.base.name + " " + instance.mapValues { it.value.methods.toString() }.toString())
        }
    }

}
