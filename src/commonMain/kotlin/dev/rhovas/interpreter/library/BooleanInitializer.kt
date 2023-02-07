package dev.rhovas.interpreter.library

import dev.rhovas.interpreter.environment.Object
import dev.rhovas.interpreter.environment.Type

object BooleanInitializer : Library.TypeInitializer("Boolean") {

    override fun initialize() {
        inherits.add(Type.ANY)

        method("negate", operator = "!",
            returns = Type.BOOLEAN,
        ) { (instance) ->
            val instance = instance.value as Boolean
            Object(Type.BOOLEAN, !instance)
        }

        method("equals", operator = "==",
            parameters = listOf("other" to Type.BOOLEAN),
            returns = Type.BOOLEAN,
        ) { (instance, other) ->
            Object(Type.BOOLEAN, instance.value == other.value)
        }

        method("toString",
            returns = Type.STRING,
        ) { (instance) ->
            Object(Type.STRING, "${instance.value}")
        }
    }

}
