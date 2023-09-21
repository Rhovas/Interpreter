package dev.rhovas.interpreter.library

import dev.rhovas.interpreter.environment.Component
import dev.rhovas.interpreter.environment.Object
import dev.rhovas.interpreter.environment.Type

object BooleanInitializer : Library.ComponentInitializer(Component.Class("Boolean")) {

    override fun declare() {
        inherits.add(Type.HASHABLE[Type.BOOLEAN])
    }

    override fun define() {
        method("negate", operator = "!",
            parameters = listOf(),
            returns = Type.BOOLEAN,
        ) { (instance): T1<Boolean> ->
            Object(Type.BOOLEAN, !instance)
        }
    }

}
