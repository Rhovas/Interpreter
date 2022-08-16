package dev.rhovas.interpreter.library

import dev.rhovas.interpreter.EVALUATOR
import dev.rhovas.interpreter.environment.Object
import dev.rhovas.interpreter.parser.rhovas.RhovasAst
import java.math.BigDecimal
import java.math.BigInteger

object StringInitializer : Library.TypeInitializer("String") {

    override fun initialize() {
        inherits.add(type("Any"))

        method("size",
            returns = type("Integer"),
        ) { (instance) ->
            val instance = instance.value as String
            Object(type("Integer"), instance.length.toBigInteger())
        }

        method("slice",
            parameters = listOf("start" to type("Integer")),
            returns = type("List", generic("T")),
        ) { (instance, start) ->
            val instance = instance.value as String
            val start = start.value as BigInteger
            EVALUATOR.require(start >= BigInteger.ZERO && start <= instance.length.toBigInteger()) { EVALUATOR.error(
                null,
                "Invalid index.",
                "Expected a start index in range [0, ${instance.length}), but received ${start}.",
            ) }
            Object(type("String"), instance.substring(start.toInt()))
        }

        method("slice",
            parameters = listOf("start" to type("Integer"), "end" to type("Integer")),
            returns = type("List", generic("T")),
        ) { (instance, start, end) ->
            val instance = instance.value as String
            val start = start.value as BigInteger
            val end = end.value as BigInteger
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
            Object(type("String"), instance.substring(start.toInt(), end.toInt()))
        }

        method("contains",
            parameters = listOf("other" to type("String")),
            returns = type("Boolean"),
        ) { (instance, other) ->
            val instance = instance.value as String
            val other = other.value as String
            Object(type("Boolean"), instance.contains(other))
        }

        method("replace",
            parameters = listOf("original" to type("String"), "value" to type("String")),
            returns = type("String"),
        ) { (instance, original, value) ->
            val instance = instance.value as String
            val original = original.value as String
            val value = value.value as String
            Object(type("String"), instance.replace(original, value))
        }

        method("concat", operator = "+",
            parameters = listOf("other" to type("String")),
            returns = type("String"),
        ) { (instance, other) ->
            val instance = instance.value as String
            val other = other.value as String
            Object(type("String"), instance + other)
        }

        method("equals", operator = "==",
            parameters = listOf("other" to type("String")),
            returns = type("Boolean"),
        ) { (instance, other) ->
            Object(type("Boolean"), instance.value == other.value)
        }

        method("compare", operator = "<=>",
            parameters = listOf("other" to type("String")),
            returns = type("Integer"),
        ) { (instance, other) ->
            val instance = instance.value as String
            val other = other.value as String
            Object(type("Integer"), BigInteger.valueOf(instance.compareTo(other).toLong()))
        }

        method("toString",
            returns = type("String"),
        ) { (instance) ->
            Object(type("String"), "${instance.value}")
        }

        method("toAtom",
            returns = type("Atom"),
        ) { (instance) ->
            val instance = instance.value as String
            Object(type("Atom"), instance)
        }
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

}
