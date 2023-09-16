package dev.rhovas.interpreter.library

import dev.rhovas.interpreter.environment.Component
import dev.rhovas.interpreter.environment.Object
import dev.rhovas.interpreter.environment.Type

object BooleanInitializer : Library.ComponentInitializer(Component.Class("Boolean")) {

    override fun initialize() {
        inherits.add(Type.HASHABLE[Type.BOOLEAN])

        method("negate", operator = "!",
            parameters = listOf(),
            returns = Type.BOOLEAN,
        ) { (instance): T1<Boolean> ->
            Object(Type.BOOLEAN, !instance)
        }
    }

}
