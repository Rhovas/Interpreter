package dev.rhovas.interpreter.library

import dev.rhovas.interpreter.environment.Function
import dev.rhovas.interpreter.environment.Object

object BooleanInitializer : Library.TypeInitializer("Boolean") {

    override fun initialize() {
        type.methods.define(Function("negate", 1) { arguments ->
            Object(Library.TYPES["Boolean"]!!, (arguments[0].value as Boolean).not())
        })
        type.methods.define(type.methods["negate", 1]!!.copy(name = "!"))
        type.methods.define(Function("toString", 1) { arguments ->
            Object(Library.TYPES["String"]!!, arguments[0].value.toString())
        })
    }

}
