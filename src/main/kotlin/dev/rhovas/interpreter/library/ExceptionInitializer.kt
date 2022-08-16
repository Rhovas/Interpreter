package dev.rhovas.interpreter.library

import dev.rhovas.interpreter.environment.Object

object ExceptionInitializer : Library.TypeInitializer("Exception") {

    override fun initialize() {
        inherits.add(type("Any"))

        method("message",
            returns = type("String"),
        ) { (instance) ->
            Object(type("String"), "${instance.value}")
        }

        method("toString",
            returns = type("String"),
        ) { (instance) ->
            //TODO: Stacktrace
            Object(type("String"), "${instance.value}")
        }
    }

}
