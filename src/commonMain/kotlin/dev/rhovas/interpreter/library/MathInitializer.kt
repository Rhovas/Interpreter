package dev.rhovas.interpreter.library

import com.ionspin.kotlin.bignum.decimal.BigDecimal
import com.ionspin.kotlin.bignum.integer.BigInteger
import dev.rhovas.interpreter.environment.Object
import dev.rhovas.interpreter.environment.Type
import kotlin.math.pow
import kotlin.random.Random

object MathInitializer : Library.TypeInitializer("Math", Type.Component.CLASS) {

    override fun initialize() {
        variable("pi", Type.DECIMAL, BigDecimal.fromDouble(kotlin.math.PI))
        variable("e", Type.DECIMAL, BigDecimal.fromDouble(kotlin.math.E))

        function("random",
            returns = Type.DECIMAL
        ) {
            Object(Type.DECIMAL, BigDecimal.fromDouble(Random.nextDouble()))
        }

        function("random",
            parameters = listOf("until" to Type.INTEGER),
            returns = Type.DECIMAL
        ) { (until) ->
            val until = until.value as BigInteger
            Object(Type.DECIMAL, BigInteger.fromInt(Random.nextInt(until.intValue(false))))
        }

        function("pow",
            parameters = listOf("base" to Type.INTEGER, "exponent" to Type.INTEGER),
            returns = Type.INTEGER,
        ) { (base, exponent) ->
            val base = base.value as BigInteger
            val exponent = exponent.value as BigInteger
            Object(Type.INTEGER, base.pow(exponent.longValue(false)))
        }

        function("pow",
            parameters = listOf("base" to Type.DECIMAL, "exponent" to Type.DECIMAL),
            returns = Type.DECIMAL,
        ) { (base, exponent) ->
            val base = base.value as BigDecimal
            val exponent = exponent.value as BigDecimal
            Object(Type.DECIMAL, BigDecimal.fromDouble(base.doubleValue().pow(exponent.doubleValue(false))))
        }

        function("sqrt",
            parameters = listOf("num" to Type.DECIMAL),
            returns = Type.DECIMAL,
        ) { (num) ->
            val num = num.value as BigDecimal
            Object(Type.DECIMAL, BigDecimal.fromDouble(kotlin.math.sqrt(num.doubleValue(false))))
        }

        function("cbrt",
            parameters = listOf("num" to Type.DECIMAL),
            returns = Type.DECIMAL,
        ) { (num) ->
            val num = num.value as BigDecimal
            Object(Type.DECIMAL, BigDecimal.fromDouble(kotlin.math.cbrt(num.doubleValue(false))))
        }

        function("log",
            parameters = listOf("num" to Type.DECIMAL, "base" to Type.DECIMAL),
            returns = Type.DECIMAL,
        ) { (num, base) ->
            val num = num.value as BigDecimal
            val base = base.value as BigDecimal
            Object(Type.DECIMAL, BigDecimal.fromDouble(kotlin.math.log(num.doubleValue(false), base.doubleValue(false))))
        }

        function("sin",
            parameters = listOf("num" to Type.DECIMAL),
            returns = Type.DECIMAL,
        ) { (num) ->
            val num = num.value as BigDecimal
            Object(Type.DECIMAL, BigDecimal.fromDouble(kotlin.math.sin(num.doubleValue(false))))
        }

        function("cos",
            parameters = listOf("num" to Type.DECIMAL),
            returns = Type.DECIMAL,
        ) { (num) ->
            val num = num.value as BigDecimal
            Object(Type.DECIMAL, BigDecimal.fromDouble(kotlin.math.cos(num.doubleValue(false))))
        }

        function("tan",
            parameters = listOf("num" to Type.DECIMAL),
            returns = Type.DECIMAL,
        ) { (num) ->
            val num = num.value as BigDecimal
            Object(Type.DECIMAL, BigDecimal.fromDouble(kotlin.math.tan(num.doubleValue(false))))
        }

    }

}
