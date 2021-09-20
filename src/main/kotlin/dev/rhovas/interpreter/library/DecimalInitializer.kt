package dev.rhovas.interpreter.library

import dev.rhovas.interpreter.environment.Function
import dev.rhovas.interpreter.environment.Object
import java.math.BigDecimal
import java.math.BigInteger

object DecimalInitializer : Library.TypeInitializer("Decimal") {

    override fun initialize() {
        type.methods.define(Function("negate", 1) { arguments ->
            Object(Library.TYPES["Integer"]!!, (arguments[0].value as BigDecimal).negate())
        })
        type.methods.define(type.methods["negate", 1]!!.copy(name = "-"))

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
