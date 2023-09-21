package dev.rhovas.interpreter.library

import dev.rhovas.interpreter.environment.Component

object DynamicInitializer : Library.ComponentInitializer(Component.Interface("Dynamic")) {

    override fun declare() {}

    override fun define() {}

}
