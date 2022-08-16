package dev.rhovas.interpreter.library

import dev.rhovas.interpreter.environment.Object
import java.math.BigDecimal
import java.math.BigInteger
import java.math.RoundingMode

object DecimalInitializer : Library.TypeInitializer("Decimal") {

    override fun initialize() {
        inherits.add(type("Any"))

        method("negate", operator = "-",
            returns = type("Decimal"),
        ) { (instance) ->
            val instance = instance.value as BigDecimal
            Object(type("Decimal"), instance.negate())
        }

        method("add", operator = "+",
            parameters = listOf("other" to type("Decimal")),
            returns = type("Decimal"),
        ) { (instance, other) ->
            val instance = instance.value as BigDecimal
            val other = other.value as BigDecimal
            Object(type("Decimal"), instance.add(other))
        }

        method("subtract", operator = "-",
            parameters = listOf("instance" to type("Decimal")),
            returns = type("Decimal"),
        ) { (instance, other) ->
            val instance = instance.value as BigDecimal
            val other = other.value as BigDecimal
            Object(type("Decimal"), instance.subtract(other))
        }

        method("multiply", operator = "*",
            parameters = listOf("other" to type("Decimal")),
            returns = type("Decimal"),
        ) { (instance, other) ->
            val instance = instance.value as BigDecimal
            val other = other.value as BigDecimal
            Object(type("Decimal"), instance.multiply(other))
        }

        method("divide", operator = "/",
            parameters = listOf("other" to type("Decimal")),
            returns = type("Decimal"),
        ) { (instance, other) ->
            val instance = instance.value as BigDecimal
            val other = other.value as BigDecimal
            Object(type("Decimal"), instance.divide(other, RoundingMode.DOWN)) //TODO: Rounding specification
        }

        method("equals", operator = "==",
            parameters = listOf("other" to type("Decimal")),
            returns = type("Boolean"),
        ) { (instance, other) ->
            Object(type("Boolean"), instance.value == other.value)
        }

        method("compare", operator = "<=>",
            parameters = listOf("other" to type("Decimal")),
            returns = type("Integer"),
        ) { (instance, other) ->
            val instance = instance.value as BigDecimal
            val other = other.value as BigDecimal
            Object(type("Integer"), BigInteger.valueOf(instance.compareTo(other).toLong()))
        }

        method("toInteger",
            returns = type("Integer"),
        ) { (instance) ->
            val instance = instance.value as BigDecimal
            Object(type("Integer"), instance.toBigInteger())
        }

        method("toString",
            returns = type("String"),
        ) { (instance) ->
            Object(type("String"), "${instance.value}")
        }
    }

}
