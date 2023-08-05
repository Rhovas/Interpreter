package dev.rhovas.interpreter.library

import dev.rhovas.interpreter.environment.Modifiers
import dev.rhovas.interpreter.environment.Type

object DynamicInitializer : Library.TypeInitializer("Dynamic", Type.Component.INTERFACE, Modifiers(Modifiers.Inheritance.ABSTRACT)) {

    override fun initialize() {}

}
