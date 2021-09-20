package dev.rhovas.interpreter.library

import dev.rhovas.interpreter.environment.Function
import dev.rhovas.interpreter.environment.Object
import java.math.BigInteger

object StringInitializer : Library.TypeInitializer("String") {

    override fun initialize() {
        type.methods.define(Function("compare", 2) { arguments ->
            val self = (arguments[0].value as String)
            val other = (arguments[1].value as String)
            val value = BigInteger.valueOf(self.compareTo(other).toLong())
            Object(Library.TYPES["Integer"]!!, value)
        })
        type.methods.define(type.methods["compare", 2]!!.copy(name = "<=>"))
        type.methods.define(Function("toString", 1) { arguments ->
            arguments[0]
        })
    }

}
