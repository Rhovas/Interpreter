package dev.rhovas.interpreter.library

import dev.rhovas.interpreter.EVALUATOR
import dev.rhovas.interpreter.parser.rhovas.RhovasAst
import java.math.BigInteger

@Reflect.Type("String")
object StringInitializer : Library.TypeInitializer("String") {

    override fun initialize() {
        inherits.add(Library.TYPES["Any"]!!)
    }

    @Reflect.Property("size", type = Reflect.Type("Integer"))
    fun size(instance: String): BigInteger {
        return instance.length.toBigInteger()
    }

    @Reflect.Method("slice",
        parameters = [Reflect.Type("Integer")],
        returns = Reflect.Type("String"),
    )
    fun slice(instance: String, start: BigInteger): String {
        return slice(instance, start, instance.length.toBigInteger())
    }

    @Reflect.Method("slice",
        parameters = [Reflect.Type("Integer"), Reflect.Type("Integer")],
        returns = Reflect.Type("String"),
    )
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

    @Reflect.Method("contains",
        parameters = [Reflect.Type("String")],
        returns = Reflect.Type("Boolean"),
    )
    fun contains(instance: String, value: String): Boolean {
        return instance.contains(value)
    }

    @Reflect.Method("replace",
        parameters = [Reflect.Type("String")],
        returns = Reflect.Type("String"),
    )
    fun contains(instance: String, original: String, replacement: String): String {
        return instance.replace(original, replacement)
    }

    @Reflect.Method("concat", operator = "+",
        parameters = [Reflect.Type("String")],
        returns = Reflect.Type("String"),
    )
    fun concat(instance: String, other: String): String {
        return instance + other
    }

    @Reflect.Method("equals", operator = "==",
        parameters = [Reflect.Type("String")],
        returns = Reflect.Type("Boolean"),
    )
    fun equals(instance: String, other: String): Boolean {
        return instance == other
    }

    @Reflect.Method("compare", operator = "<=>",
        parameters = [Reflect.Type("String")],
        returns = Reflect.Type("Integer"),
    )
    fun compare(instance: String, other: String): BigInteger {
        return BigInteger.valueOf(instance.compareTo(other).toLong())
    }

    @Reflect.Method("toString", returns = Reflect.Type("String"))
    fun toString(instance: String): String {
        return instance
    }

    @Reflect.Method("toAtom", returns = Reflect.Type("Atom"))
    fun toAtom(instance: String): RhovasAst.Atom {
        //TODO: Validate identifier
        return RhovasAst.Atom(instance)
    }

}
