package dev.rhovas.interpreter.library

import dev.rhovas.interpreter.environment.Object

object VoidInitializer : Library.TypeInitializer("Void") {

    override fun initialize() {
        inherits.add(type("Any"))

        method("equals", operator = "==",
            parameters = listOf("other" to type("Void")),
            returns = type("Boolean"),
        ) { (instance, other) ->
            Object(type("Boolean"), true)
        }

        method("toString",
            returns = type("String"),
        ) { (instance) ->
            Object(type("String"), "void")
        }
    }

}
