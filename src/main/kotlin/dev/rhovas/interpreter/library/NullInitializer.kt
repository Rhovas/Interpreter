package dev.rhovas.interpreter.library

import dev.rhovas.interpreter.environment.Function
import dev.rhovas.interpreter.environment.Object

object NullInitializer : Library.TypeInitializer("Null") {

    override fun initialize() {
        type.methods.define(Function("toString", 1) { arguments ->
            Object(Library.TYPES["String"]!!, "null")
        })
    }

}
