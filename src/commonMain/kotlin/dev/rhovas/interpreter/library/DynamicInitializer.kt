package dev.rhovas.interpreter.library

import dev.rhovas.interpreter.environment.Component
import dev.rhovas.interpreter.environment.Modifiers
import dev.rhovas.interpreter.environment.Scope

object DynamicInitializer : Library.ComponentInitializer(Component.Interface("Dynamic", Modifiers(Modifiers.Inheritance.ABSTRACT), Scope.Declaration(null))) {

    override fun initialize() {}

}
