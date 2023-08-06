package dev.rhovas.interpreter.library

import dev.rhovas.interpreter.environment.Component
import dev.rhovas.interpreter.environment.Modifiers
import dev.rhovas.interpreter.environment.Object
import dev.rhovas.interpreter.environment.Scope
import dev.rhovas.interpreter.environment.Type

object BooleanInitializer : Library.ComponentInitializer(Component.Class("Boolean", Modifiers(Modifiers.Inheritance.DEFAULT), Scope.Definition(null))) {

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
