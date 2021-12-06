package dev.rhovas.interpreter.library

import java.math.BigDecimal
import java.math.BigInteger

object IntegerInitializer : Library.TypeInitializer("Integer") {

    @Reflect.Method("negate", operator = "-", returns = "Integer")
    fun negate(instance: BigInteger): BigInteger {
        return instance.negate()
    }

    @Reflect.Method("add", operator = "+", parameters = ["Integer"], returns = "Integer")
    fun add(instance: BigInteger, other: BigInteger): BigInteger {
        return instance.add(other)
    }

    @Reflect.Method("subtract", operator = "-", parameters = ["Integer"], returns = "Integer")
    fun subtract(instance: BigInteger, other: BigInteger): BigInteger {
        return instance.subtract(other)
    }

    @Reflect.Method("multiply", operator = "*", parameters = ["Integer"], returns = "Integer")
    fun multiply(instance: BigInteger, other: BigInteger): BigInteger {
        return instance.multiply(other)
    }

    @Reflect.Method("divide", operator = "/", parameters = ["Integer"], returns = "Integer")
    fun divide(instance: BigInteger, other: BigInteger): BigInteger {
        return instance.divide(other)
    }

    @Reflect.Method("mod", parameters = ["Integer"], returns = "Integer")
    fun mod(instance: BigInteger, other: BigInteger): BigInteger {
        return instance.mod(other)
    }

    @Reflect.Method("equals", operator = "==", parameters = ["Integer"], returns = "Boolean")
    fun equals(instance: BigInteger, other: BigInteger): Boolean {
        return instance == other
    }

    @Reflect.Method("compare", operator = "<=>", parameters = ["Integer"], returns = "Integer")
    fun compare(instance: BigInteger, other: BigInteger): BigInteger {
        return BigInteger.valueOf(instance.compareTo(other).toLong())
    }

    @Reflect.Method("toDecimal", returns = "Decimal")
    fun toDecimal(instance: BigInteger): BigDecimal {
        return instance.toBigDecimal()
    }

    @Reflect.Method("toString", returns = "String")
    fun toString(instance: BigInteger): String {
        return instance.toString()
    }

}
