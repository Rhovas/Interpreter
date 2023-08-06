package dev.rhovas.interpreter.library

import dev.rhovas.interpreter.environment.Component
import dev.rhovas.interpreter.environment.Modifiers
import dev.rhovas.interpreter.environment.Object
import dev.rhovas.interpreter.environment.Scope
import dev.rhovas.interpreter.environment.Type

object VoidInitializer : Library.ComponentInitializer(Component.Class("Void", Modifiers(Modifiers.Inheritance.DEFAULT), Scope.Definition(null))) {

    override fun initialize() {
        inherits.add(Type.HASHABLE[Type.VOID])

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
