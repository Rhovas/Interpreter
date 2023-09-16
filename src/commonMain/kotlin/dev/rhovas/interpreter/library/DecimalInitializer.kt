package dev.rhovas.interpreter.library

import com.ionspin.kotlin.bignum.decimal.BigDecimal
import com.ionspin.kotlin.bignum.decimal.DecimalMode
import com.ionspin.kotlin.bignum.decimal.RoundingMode
import com.ionspin.kotlin.bignum.integer.BigInteger
import dev.rhovas.interpreter.environment.Component
import dev.rhovas.interpreter.environment.Object
import dev.rhovas.interpreter.environment.Type

object DecimalInitializer : Library.ComponentInitializer(Component.Class("Decimal")) {

    override fun initialize() {
        inherits.add(Type.COMPARABLE[Type.DECIMAL])
        inherits.add(Type.HASHABLE[Type.DECIMAL])

        method("abs",
            parameters = listOf(),
            returns = Type.DECIMAL,
        ) { (instance): T1<BigDecimal> ->
            Object(Type.DECIMAL, instance.abs())
        }

        method("negate", operator = "-",
            parameters = listOf(),
            returns = Type.DECIMAL,
        ) { (instance): T1<BigDecimal> ->
            Object(Type.DECIMAL, instance.negate())
        }

        method("add", operator = "+",
            parameters = listOf("other" to Type.DECIMAL),
            returns = Type.DECIMAL,
        ) { (instance, other): T2<BigDecimal, BigDecimal> ->
            Object(Type.DECIMAL, instance.add(other))
        }

        method("subtract", operator = "-",
            parameters = listOf("instance" to Type.DECIMAL),
            returns = Type.DECIMAL,
        ) { (instance, other): T2<BigDecimal, BigDecimal> ->
            Object(Type.DECIMAL, instance.subtract(other))
        }

        method("multiply", operator = "*",
            parameters = listOf("other" to Type.DECIMAL),
            returns = Type.DECIMAL,
        ) { (instance, other): T2<BigDecimal, BigDecimal> ->
            Object(Type.DECIMAL, instance.multiply(other))
        }

        method("divide", operator = "/",
            parameters = listOf("other" to Type.DECIMAL),
            returns = Type.DECIMAL,
        ) { (instance, other): T2<BigDecimal, BigDecimal> ->
            Object(Type.DECIMAL, instance.divide(other, DecimalMode(other.precision, RoundingMode.TOWARDS_ZERO, other.scale)))
        }

        method("rem",
            parameters = listOf("other" to Type.DECIMAL),
            returns = Type.DECIMAL,
        ) { (instance, other): T2<BigDecimal, BigDecimal> ->
            Object(Type.DECIMAL, instance.rem(other))
        }

        method("mod",
            parameters = listOf("other" to Type.DECIMAL),
            returns = Type.DECIMAL,
        ) { (instance, other): T2<BigDecimal, BigDecimal> ->
            val rem = instance.rem(other)
            val mod = if (rem.isNegative) rem + other else rem
            Object(Type.DECIMAL, mod)
        }

        method("to",
            parameters = listOf("type" to Type.TYPE[Type.INTEGER]),
            returns = Type.INTEGER,
        ) { (instance, _type): T2<BigDecimal, Type> ->
            Object(Type.INTEGER, instance.toBigInteger())
        }
    }

}
