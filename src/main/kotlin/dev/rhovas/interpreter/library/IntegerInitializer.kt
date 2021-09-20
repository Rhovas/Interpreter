package dev.rhovas.interpreter.library

import dev.rhovas.interpreter.environment.Function
import dev.rhovas.interpreter.environment.Object
import dev.rhovas.interpreter.evaluator.EvaluateException
import java.math.BigInteger

object IntegerInitializer : Library.TypeInitializer("Integer") {

    override fun initialize() {
        type.methods.define(Function("negate", 1) { arguments ->
            Object(Library.TYPES["Integer"]!!, (arguments[0].value as BigInteger).negate())
        })
        type.methods.define(type.methods["negate", 1]!!.copy(name = "-"))

        type.methods.define(Function("add", 2) { arguments ->
            if (arguments[1].type != type) {
                throw EvaluateException("Integer#add is not supported with argument ${arguments[1].type.name}")
            }
            Object(Library.TYPES["Integer"]!!, (arguments[0].value as BigInteger).add(arguments[1].value as BigInteger))
        })
        type.methods.define(type.methods["add", 2]!!.copy(name = "+"))

        type.methods.define(Function("subtract", 2) { arguments ->
            if (arguments[1].type != type) {
                throw EvaluateException("Integer#subtract is not supported with argument ${arguments[1].type.name}")
            }
            Object(Library.TYPES["Integer"]!!, (arguments[0].value as BigInteger).subtract(arguments[1].value as BigInteger))
        })
        type.methods.define(type.methods["subtract", 2]!!.copy(name = "-"))

        type.methods.define(Function("multiply", 2) { arguments ->
            if (arguments[1].type != type) {
                throw EvaluateException("Integer#multiply is not supported with argument ${arguments[1].type.name}")
            }
            Object(Library.TYPES["Integer"]!!, (arguments[0].value as BigInteger).multiply(arguments[1].value as BigInteger))
        })
        type.methods.define(type.methods["multiply", 2]!!.copy(name = "*"))

        type.methods.define(Function("divide", 2) { arguments ->
            if (arguments[1].type != type) {
                throw EvaluateException("Integer#divide is not supported with argument ${arguments[1].type.name}")
            }
            Object(Library.TYPES["Integer"]!!, (arguments[0].value as BigInteger).divide(arguments[1].value as BigInteger))
        })
        type.methods.define(type.methods["divide", 2]!!.copy(name = "/"))

        type.methods.define(Function("equals", 2) { arguments ->
            Object(Library.TYPES["Boolean"]!!, arguments[0].value == arguments[1].value)
        })
        type.methods.define(type.methods["equals", 2]!!.copy(name = "=="))

        type.methods.define(Function("compare", 2) { arguments ->
            val self = arguments[0].value as BigInteger
            val other = arguments[1].value as BigInteger
            Object(Library.TYPES["Integer"]!!, BigInteger.valueOf(self.compareTo(other).toLong()))
        })
        type.methods.define(type.methods["compare", 2]!!.copy(name = "<=>"))

        type.methods.define(Function("toString", 1) { arguments ->
            Object(Library.TYPES["String"]!!, arguments[0].value.toString())
        })
    }

}
