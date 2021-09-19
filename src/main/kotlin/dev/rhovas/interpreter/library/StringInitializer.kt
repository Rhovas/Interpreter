package dev.rhovas.interpreter.library

import dev.rhovas.interpreter.environment.Function

object StringInitializer : Library.TypeInitializer("String") {

    override fun initialize() {
        type.methods.define(Function("toString", 1) { arguments ->
            arguments[0]
        })
    }

}
