package dev.rhovas.interpreter.library

import dev.rhovas.interpreter.environment.Object
import dev.rhovas.interpreter.environment.Type

object VoidInitializer : Library.TypeInitializer("Void") {

    override fun initialize() {
        inherits.add(Type.ANY)

        method("equals", operator = "==",
            parameters = listOf("other" to Type.VOID),
            returns = Type.BOOLEAN,
        ) { (instance, other) ->
            Object(Type.BOOLEAN, true)
        }

        method("to",
            parameters = listOf("type" to Type.TYPE[Type.STRING]),
            returns = Type.STRING,
        ) { (instance) ->
            Object(Type.STRING, "void")
        }
    }

}
