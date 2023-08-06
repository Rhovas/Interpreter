package dev.rhovas.interpreter.library

import com.ionspin.kotlin.bignum.decimal.BigDecimal
import com.ionspin.kotlin.bignum.integer.BigInteger
import dev.rhovas.interpreter.environment.Component
import dev.rhovas.interpreter.environment.Modifiers
import dev.rhovas.interpreter.environment.Object
import dev.rhovas.interpreter.environment.Scope
import dev.rhovas.interpreter.environment.Type

object IntegerInitializer : Library.ComponentInitializer(Component.Class("Integer", Modifiers(Modifiers.Inheritance.DEFAULT), Scope.Definition(null))) {

    override fun initialize() {
        inherits.add(Type.COMPARABLE[Type.INTEGER])
        inherits.add(Type.HASHABLE[Type.INTEGER])

        method("abs",
            returns = Type.INTEGER,
        ) { (instance) ->
            val instance = instance.value as BigInteger
            Object(Type.INTEGER, instance.abs())
        }

        method("negate", operator = "-",
            returns = Type.INTEGER,
        ) { (instance) ->
            val instance = instance.value as BigInteger
            Object(Type.INTEGER, instance.negate())
        }

        method("add", operator = "+",
            parameters = listOf("other" to Type.INTEGER),
            returns = Type.INTEGER,
        ) { (instance, other) ->
            val instance = instance.value as BigInteger
            val other = other.value as BigInteger
            Object(Type.INTEGER, instance.add(other))
        }

        method("subtract", operator = "-",
            parameters = listOf("other" to Type.INTEGER),
            returns = Type.INTEGER,
        ) { (instance, other) ->
            val instance = instance.value as BigInteger
            val other = other.value as BigInteger
            Object(Type.INTEGER, instance.subtract(other))
        }

        method("multiply", operator = "*",
            parameters = listOf("other" to Type.INTEGER),
            returns = Type.INTEGER,
        ) { (instance, other) ->
            val instance = instance.value as BigInteger
            val other = other.value as BigInteger
            Object(Type.INTEGER, instance.multiply(other))
        }

        method("divide", operator = "/",
            parameters = listOf("other" to Type.INTEGER),
            returns = Type.INTEGER,
        ) { (instance, other) ->
            val instance = instance.value as BigInteger
            val other = other.value as BigInteger
            Object(Type.INTEGER, instance.divide(other))
        }

        method("rem",
            parameters = listOf("other" to Type.INTEGER),
            returns = Type.INTEGER,
        ) { (instance, other) ->
            val instance = instance.value as BigInteger
            val other = other.value as BigInteger
            Object(Type.INTEGER, instance.rem(other))
        }

        method("mod",
            parameters = listOf("other" to Type.INTEGER),
            returns = Type.INTEGER,
        ) { (instance, other) ->
            val instance = instance.value as BigInteger
            val other = other.value as BigInteger
            Object(Type.INTEGER, instance.mod(other))
        }

        method("to",
            parameters = listOf("type" to Type.TYPE[Type.DECIMAL]),
            returns = Type.DECIMAL,
        ) { (instance) ->
            val instance = instance.value as BigInteger
            Object(Type.DECIMAL, BigDecimal.fromBigInteger(instance))
        }
    }

}
