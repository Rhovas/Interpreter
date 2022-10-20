package dev.rhovas.interpreter.library

import dev.rhovas.interpreter.environment.Object

object BooleanInitializer : Library.TypeInitializer("Boolean") {

    override fun initialize() {
        inherits.add(type("Any"))

        method("negate", operator = "!",
            returns = type("Boolean"),
        ) { (instance) ->
            val instance = instance.value as Boolean
            Object(type("Boolean"), !instance)
        }

        method("equals", operator = "==",
            parameters = listOf("other" to type("Boolean")),
            returns = type("Boolean"),
        ) { (instance, other) ->
            Object(type("Boolean"), instance.value == other.value)
        }

        method("toString",
            returns = type("String"),
        ) { (instance) ->
            Object(type("String"), "${instance.value}")
        }
    }

}
