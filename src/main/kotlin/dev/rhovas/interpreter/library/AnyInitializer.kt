package dev.rhovas.interpreter.library

import dev.rhovas.interpreter.environment.Object

object AnyInitializer: Library.TypeInitializer("Any") {

    override fun initialize() {
        method("toString",
            returns = type("String"),
        ) { (instance) ->
            Object(type("String"), "${instance.value}")
        }
    }

}
