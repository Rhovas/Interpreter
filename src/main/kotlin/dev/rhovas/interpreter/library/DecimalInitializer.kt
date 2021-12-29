package dev.rhovas.interpreter.library

import java.math.BigDecimal
import java.math.BigInteger
import java.math.RoundingMode

@Reflect.Type("Decimal")
object DecimalInitializer : Library.TypeInitializer("Decimal") {

    override fun initialize() {
        inherits.add(Library.TYPES["Any"]!!)
    }

    @Reflect.Method("negate", operator = "-", returns = Reflect.Type("Decimal"))
    fun negate(instance: BigDecimal): BigDecimal {
        return instance.negate()
    }

    @Reflect.Method("add", operator = "+",
        parameters = [Reflect.Type("Decimal")],
        returns = Reflect.Type("Decimal")
    )
    fun add(instance: BigDecimal, other: BigDecimal): BigDecimal {
        return instance.add(other)
    }

    @Reflect.Method("subtract", operator = "-",
        parameters = [Reflect.Type("Decimal")],
        returns = Reflect.Type("Decimal")
    )
    fun subtract(instance: BigDecimal, other: BigDecimal): BigDecimal {
        return instance.subtract(other)
    }

    @Reflect.Method("multiply", operator = "*",
        parameters = [Reflect.Type("Decimal")],
        returns = Reflect.Type("Decimal")
    )
    fun multiply(instance: BigDecimal, other: BigDecimal): BigDecimal {
        return instance.multiply(other)
    }

    @Reflect.Method("divide", operator = "/",
        parameters = [Reflect.Type("Decimal")],
        returns = Reflect.Type("Decimal")
    )
    fun divide(instance: BigDecimal, other: BigDecimal): BigDecimal {
        return instance.divide(other, RoundingMode.DOWN) //TODO: Rounding specification
    }

    @Reflect.Method("equals", operator = "==",
        parameters = [Reflect.Type("Decimal")],
        returns = Reflect.Type("Boolean"),
    )
    fun equals(instance: BigDecimal, other: BigDecimal): Boolean {
        return instance == other
    }

    @Reflect.Method("compare", operator = "<=>",
        parameters = [Reflect.Type("Decimal")],
        returns = Reflect.Type("Integer")
    )
    fun compare(instance: BigDecimal, other: BigDecimal): BigInteger {
        return BigInteger.valueOf(instance.compareTo(other).toLong())
    }

    @Reflect.Method("toString", returns = Reflect.Type("String"))
    fun toString(instance: BigDecimal): String {
        return instance.toString()
    }

}
