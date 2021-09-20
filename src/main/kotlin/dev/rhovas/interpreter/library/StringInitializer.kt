package dev.rhovas.interpreter.library

import dev.rhovas.interpreter.environment.Function
import dev.rhovas.interpreter.environment.Object
import dev.rhovas.interpreter.evaluator.EvaluateException
import java.math.BigInteger

object StringInitializer : Library.TypeInitializer("String") {

    override fun initialize() {
        type.methods.define(Function("concat", 2) { arguments ->
            val other = arguments[1].methods["toString", 0]!!.invoke(listOf())
            Object(Library.TYPES["String"]!!, arguments[0].value as String + other.value as String)
        })
        type.methods.define(type.methods["concat", 2]!!.copy(name = "+"))

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
