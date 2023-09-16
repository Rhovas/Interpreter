package dev.rhovas.interpreter.library

import dev.rhovas.interpreter.environment.Component
import dev.rhovas.interpreter.environment.Object
import dev.rhovas.interpreter.environment.Type

object VoidInitializer : Library.ComponentInitializer(Component.Class("Void")) {

    override fun initialize() {
        inherits.add(Type.HASHABLE[Type.VOID])

        method("equals", operator = "==",
            parameters = listOf("other" to Type.VOID),
            returns = Type.BOOLEAN,
        ) { (_instance, _other): T2<Unit, Unit> ->
            Object(Type.BOOLEAN, true)
        }

        method("to",
            parameters = listOf("type" to Type.TYPE[Type.STRING]),
            returns = Type.STRING,
        ) { (_instance, _type): T2<Unit, Type> ->
            Object(Type.STRING, "void")
        }
    }

}
