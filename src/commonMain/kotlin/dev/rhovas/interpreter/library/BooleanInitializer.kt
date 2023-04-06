package dev.rhovas.interpreter.library

import dev.rhovas.interpreter.environment.Object
import dev.rhovas.interpreter.environment.Type

object BooleanInitializer : Library.TypeInitializer("Boolean") {

    override fun initialize() {
        inherits.add(Type.EQUATABLE[Type.BOOLEAN])

        method("negate", operator = "!",
            returns = Type.BOOLEAN,
        ) { (instance) ->
            val instance = instance.value as Boolean
            Object(Type.BOOLEAN, !instance)
        }

        method("to",
            parameters = listOf("type" to Type.TYPE[Type.STRING]),
            returns = Type.STRING,
        ) { (instance) ->
            Object(Type.STRING, "${instance.value}")
        }
    }

}
