package dev.rhovas.interpreter.library

import dev.rhovas.interpreter.environment.Object
import dev.rhovas.interpreter.environment.Type

object NullInitializer : Library.TypeInitializer("Null") {

    override fun initialize() {
        inherits.add(Type.ANY)

        method("equals", operator = "==",
            parameters = listOf("other" to Type.NULL),
            returns = Type.BOOLEAN,
        ) { (instance, other) ->
            Object(Type.BOOLEAN, true)
        }

        method("toString",
            returns = Type.STRING,
        ) { (instance) ->
            Object(Type.STRING, "null")
        }
    }

}
