package dev.rhovas.interpreter.library

import dev.rhovas.interpreter.environment.Object
import dev.rhovas.interpreter.environment.Type

object BooleanInitializer : Library.TypeInitializer("Boolean", Type.Component.CLASS) {

    override fun initialize() {
        inherits.add(Type.HASHABLE[Type.BOOLEAN])

        method("negate", operator = "!",
            returns = Type.BOOLEAN,
        ) { (instance) ->
            val instance = instance.value as Boolean
            Object(Type.BOOLEAN, !instance)
        }
    }

}
