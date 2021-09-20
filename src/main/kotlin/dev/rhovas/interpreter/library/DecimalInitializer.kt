package dev.rhovas.interpreter.library

import dev.rhovas.interpreter.environment.Function
import dev.rhovas.interpreter.environment.Object
import dev.rhovas.interpreter.evaluator.EvaluateException
import java.math.BigDecimal
import java.math.BigInteger
import java.math.RoundingMode

object DecimalInitializer : Library.TypeInitializer("Decimal") {

    override fun initialize() {
        type.methods.define(Function("negate", 1) { arguments ->
            Object(Library.TYPES["Integer"]!!, (arguments[0].value as BigDecimal).negate())
        })
        type.methods.define(type.methods["negate", 1]!!.copy(name = "-"))

        type.methods.define(Function("add", 2) { arguments ->
            if (arguments[1].type != type) {
                throw EvaluateException("Decimal#add is not supported with argument ${arguments[1].type.name}")
            }
            Object(Library.TYPES["Decimal"]!!, (arguments[0].value as BigDecimal).add(arguments[1].value as BigDecimal))
        })
        type.methods.define(type.methods["add", 2]!!.copy(name = "+"))

        type.methods.define(Function("subtract", 2) { arguments ->
            if (arguments[1].type != type) {
                throw EvaluateException("Decimal#subtract is not supported with argument ${arguments[1].type.name}")
            }
            Object(Library.TYPES["Decimal"]!!, (arguments[0].value as BigDecimal).subtract(arguments[1].value as BigDecimal))
        })
        type.methods.define(type.methods["subtract", 2]!!.copy(name = "-"))

        type.methods.define(Function("multiply", 2) { arguments ->
            if (arguments[1].type != type) {
                throw EvaluateException("Decimal#multiply is not supported with argument ${arguments[1].type.name}")
            }
            Object(Library.TYPES["Decimal"]!!, (arguments[0].value as BigDecimal).multiply(arguments[1].value as BigDecimal))
        })
        type.methods.define(type.methods["multiply", 2]!!.copy(name = "*"))

        type.methods.define(Function("divide", 2) { arguments ->
            if (arguments[1].type != type) {
                throw EvaluateException("Decimal#divide is not supported with argument ${arguments[1].type.name}")
            }
            //TODO: Rounding specification
            Object(Library.TYPES["Decimal"]!!, (arguments[0].value as BigDecimal).divide(arguments[1].value as BigDecimal, RoundingMode.DOWN))
        })
        type.methods.define(type.methods["divide", 2]!!.copy(name = "/"))

        type.methods.define(Function("equals", 2) { arguments ->
            Object(Library.TYPES["Boolean"]!!, arguments[0].value == arguments[1].value)
        })
        type.methods.define(type.methods["equals", 2]!!.copy(name = "=="))

        type.methods.define(Function("compare", 2) { arguments ->
            val self = arguments[0].value as BigDecimal
            val other = arguments[1].value as BigDecimal
            Object(Library.TYPES["Integer"]!!, BigInteger.valueOf(self.compareTo(other).toLong()))
        })
        type.methods.define(type.methods["compare", 2]!!.copy(name = "<=>"))

        type.methods.define(Function("toString", 1) { arguments ->
            Object(Library.TYPES["String"]!!, arguments[0].value.toString())
        })
    }

}
