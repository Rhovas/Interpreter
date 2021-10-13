package dev.rhovas.interpreter.library

import java.math.BigInteger

object StringInitializer : Library.TypeInitializer("String") {

    @Reflect.Method("concat", operator = "+", parameters = ["String"], returns = "String")
    fun concat(instance: String, other: String): String {
        return instance + other
    }

    @Reflect.Method("compare", operator = "<=>", parameters = ["String"], returns = "Integer")
    fun compare(instance: String, other: String): BigInteger {
        return BigInteger.valueOf(instance.compareTo(other).toLong())
    }

    @Reflect.Method("equals", operator = "==", parameters = ["String"], returns = "Boolean")
    fun equals(instance: String, other: String): Boolean {
        return true
    }

    @Reflect.Method("toString", returns = "String")
    fun toString(instance: String): String {
        return instance
    }

}
