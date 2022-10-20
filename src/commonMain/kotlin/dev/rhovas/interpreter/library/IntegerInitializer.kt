package dev.rhovas.interpreter.library

import com.ionspin.kotlin.bignum.decimal.BigDecimal
import com.ionspin.kotlin.bignum.integer.BigInteger
import dev.rhovas.interpreter.environment.Object

object IntegerInitializer : Library.TypeInitializer("Integer") {

    override fun initialize() {
        inherits.add(type("Any"))

        method("negate", operator = "-",
            returns = type("Integer"),
        ) { (instance) ->
            val instance = instance.value as BigInteger
            Object(type("Integer"), instance.negate())
        }

        method("add", operator = "+",
            parameters = listOf("other" to type("Integer")),
            returns = type("Integer"),
        ) { (instance, other) ->
            val instance = instance.value as BigInteger
            val other = other.value as BigInteger
            Object(type("Integer"), instance.add(other))
        }

        method("subtract", operator = "-",
            parameters = listOf("other" to type("Integer")),
            returns = type("Integer"),
        ) { (instance, other) ->
            val instance = instance.value as BigInteger
            val other = other.value as BigInteger
            Object(type("Integer"), instance.subtract(other))
        }

        method("multiply", operator = "*",
            parameters = listOf("other" to type("Integer")),
            returns = type("Integer"),
        ) { (instance, other) ->
            val instance = instance.value as BigInteger
            val other = other.value as BigInteger
            Object(type("Integer"), instance.multiply(other))
        }

        method("divide", operator = "/",
            parameters = listOf("other" to type("Integer")),
            returns = type("Integer"),
        ) { (instance, other) ->
            val instance = instance.value as BigInteger
            val other = other.value as BigInteger
            Object(type("Integer"), instance.divide(other))
        }

        method("mod",
            parameters = listOf("other" to type("Integer")),
            returns = type("Integer"),
        ) { (instance, other) ->
            val instance = instance.value as BigInteger
            val other = other.value as BigInteger
            Object(type("Integer"), instance.mod(other))
        }

        method("equals", operator = "==",
            parameters = listOf("other" to type("Integer")),
            returns = type("Boolean"),
        ) { (instance, other) ->
            Object(type("Boolean"), instance.value == other.value)
        }

        method("compare", operator = "<=>",
            parameters = listOf("other" to type("Integer")),
            returns = type("Integer"),
        ) { (instance, other) ->
            val instance = instance.value as BigInteger
            val other = other.value as BigInteger
            Object(type("Integer"), BigInteger.fromInt(instance.compareTo(other)))
        }

        method("toDecimal",
            returns = type("Decimal"),
        ) { (instance) ->
            val instance = instance.value as BigInteger
            Object(type("Decimal"), BigDecimal.fromBigInteger(instance))
        }

        method("toString",
            returns = type("String"),
        ) { (instance) ->
            Object(type("String"), "${instance.value}")
        }
    }

}
