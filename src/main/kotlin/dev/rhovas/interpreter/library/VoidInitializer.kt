package dev.rhovas.interpreter.library

import dev.rhovas.interpreter.environment.Function
import dev.rhovas.interpreter.environment.Object

object VoidInitializer : Library.TypeInitializer("Void") {

    override fun initialize() {
        type.methods.define(Function("toString", 1) { arguments ->
            Object(Library.TYPES["String"]!!, "void")
        })
    }

}
