package dev.rhovas.interpreter.library

import dev.rhovas.interpreter.environment.Function
import dev.rhovas.interpreter.environment.Object
import java.math.BigInteger

object IntegerInitializer : Library.TypeInitializer("Integer") {

    override fun initialize() {
        type.methods.define(Function("negate", 1) { arguments ->
            Object(Library.TYPES["Integer"]!!, (arguments[0].value as BigInteger).negate())
        })
        type.methods.define(type.methods["negate", 1]!!.copy(name = "-"))
        type.methods.define(Function("toString", 1) { arguments ->
            Object(Library.TYPES["String"]!!, arguments[0].value.toString())
        })
    }

}
