package dev.rhovas.interpreter.library

import dev.rhovas.interpreter.environment.Object

object NullInitializer : Library.TypeInitializer("Null") {

    override fun initialize() {
        inherits.add(type("Any"))

        method("equals", operator = "==",
            parameters = listOf("other" to type("Null")),
            returns = type("Boolean"),
        ) { (instance, other) ->
            Object(type("Boolean"), true)
        }

        method("toString",
            returns = type("String"),
        ) { (instance) ->
            Object(type("String"), "null")
        }
    }

}
