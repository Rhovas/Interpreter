package dev.rhovas.interpreter.library

import java.math.BigDecimal
import java.math.BigInteger
import java.math.RoundingMode

object DecimalInitializer : Library.TypeInitializer("Decimal") {

    @Reflect.Method("negate", operator = "-", returns = "Decimal")
    fun negate(instance: BigDecimal): BigDecimal {
        return instance.negate()
    }

    @Reflect.Method("add", operator = "+", parameters = ["Decimal"], returns = "Decimal")
    fun add(instance: BigDecimal, other: BigDecimal): BigDecimal {
        return instance.add(other)
    }

    @Reflect.Method("subtract", operator = "-", parameters = ["Decimal"], returns = "Decimal")
    fun subtract(instance: BigDecimal, other: BigDecimal): BigDecimal {
        return instance.subtract(other)
    }

    @Reflect.Method("multiply", operator = "*", parameters = ["Decimal"], returns = "Decimal")
    fun multiply(instance: BigDecimal, other: BigDecimal): BigDecimal {
        return instance.multiply(other)
    }

    @Reflect.Method("divide", operator = "/", parameters = ["Decimal"], returns = "Decimal")
    fun divide(instance: BigDecimal, other: BigDecimal): BigDecimal {
        return instance.divide(other, RoundingMode.DOWN) //TODO: Rounding specification
    }

    @Reflect.Method("equals", operator = "==", parameters = ["Decimal"], returns = "Boolean")
    fun equals(instance: BigDecimal, other: BigDecimal): Boolean {
        return instance == other
    }

    @Reflect.Method("compare", operator = "<=>", parameters = ["Decimal"], returns = "Integer")
    fun compare(instance: BigDecimal, other: BigDecimal): BigInteger {
        return BigInteger.valueOf(instance.compareTo(other).toLong())
    }

    @Reflect.Method("toString", returns = "String")
    fun toString(instance: BigDecimal): String {
        return instance.toString()
    }

}
