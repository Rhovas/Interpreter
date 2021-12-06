package dev.rhovas.interpreter.library

import dev.rhovas.interpreter.EVALUATOR
import dev.rhovas.interpreter.parser.rhovas.RhovasAst
import java.math.BigInteger

object StringInitializer : Library.TypeInitializer("String") {

    @Reflect.Property("size", type = "Integer")
    fun size(instance: String): BigInteger {
        return instance.length.toBigInteger()
    }

    @Reflect.Method("slice", parameters = ["Integer"], returns = "String")
    fun slice(instance: String, start: BigInteger): String {
        return slice(instance, start, instance.length.toBigInteger())
    }

    @Reflect.Method("slice", parameters = ["Integer", "Integer"], returns = "String")
    fun slice(instance: String, start: BigInteger, end: BigInteger): String {
        //TODO: Consider supporting negative indices
        EVALUATOR.require(start >= BigInteger.ZERO && start <= instance.length.toBigInteger()) { EVALUATOR.error(
            null,
            "Invalid index.",
            "Expected a start index in range [0, ${instance.length}), but received ${start}.",
        ) }
        EVALUATOR.require(end >= start && end <= instance.length.toBigInteger()) { EVALUATOR.error(
            null,
            "Invalid index.",
            "Expected an end index in range [start = ${start}, ${instance.length}), but received ${end}.",
        ) }
        return instance.substring(start.toInt(), end.toInt())
    }

    @Reflect.Method("contains", parameters = ["String"], returns = "Boolean")
    fun contains(instance: String, value: String): Boolean {
        return instance.contains(value)
    }

    @Reflect.Method("replace", parameters = ["String"], returns = "String")
    fun contains(instance: String, original: String, replacement: String): String {
        return instance.replace(original, replacement)
    }

    @Reflect.Method("concat", operator = "+", parameters = ["String"], returns = "String")
    fun concat(instance: String, other: String): String {
        return instance + other
    }

    @Reflect.Method("equals", operator = "==", parameters = ["String"], returns = "Boolean")
    fun equals(instance: String, other: String): Boolean {
        return instance == other
    }

    @Reflect.Method("compare", operator = "<=>", parameters = ["String"], returns = "Integer")
    fun compare(instance: String, other: String): BigInteger {
        return BigInteger.valueOf(instance.compareTo(other).toLong())
    }

    @Reflect.Method("toString", returns = "String")
    fun toString(instance: String): String {
        return instance
    }

    @Reflect.Method("toAtom", returns = "Atom")
    fun toAtom(instance: String): RhovasAst.Atom {
        //TODO: Validate identifier
        return RhovasAst.Atom(instance)
    }

}
