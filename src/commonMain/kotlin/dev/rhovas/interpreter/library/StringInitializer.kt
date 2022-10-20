package dev.rhovas.interpreter.library

import com.ionspin.kotlin.bignum.integer.BigInteger
import dev.rhovas.interpreter.EVALUATOR
import dev.rhovas.interpreter.environment.Object

object StringInitializer : Library.TypeInitializer("String") {

    override fun initialize() {
        inherits.add(type("Any"))

        method("size",
            returns = type("Integer"),
        ) { (instance) ->
            val instance = instance.value as String
            Object(type("Integer"), BigInteger.fromInt(instance.length))
        }

        method("slice",
            parameters = listOf("start" to type("Integer")),
            returns = type("List", generic("T")),
        ) { (instance, start) ->
            val instance = instance.value as String
            val start = start.value as BigInteger
            EVALUATOR.require(start >= BigInteger.ZERO && start <= BigInteger.fromInt(instance.length)) { EVALUATOR.error(
                null,
                "Invalid index.",
                "Expected a start index in range [0, ${instance.length}), but received ${start}.",
            ) }
            Object(type("String"), instance.substring(start.intValue()))
        }

        method("slice",
            parameters = listOf("start" to type("Integer"), "end" to type("Integer")),
            returns = type("List", generic("T")),
        ) { (instance, start, end) ->
            val instance = instance.value as String
            val start = start.value as BigInteger
            val end = end.value as BigInteger
            EVALUATOR.require(start >= BigInteger.ZERO && start <= BigInteger.fromInt(instance.length)) { EVALUATOR.error(
                null,
                "Invalid index.",
                "Expected a start index in range [0, ${instance.length}), but received ${start}.",
            ) }
            EVALUATOR.require(end >= start && end <= BigInteger.fromInt(instance.length)) { EVALUATOR.error(
                null,
                "Invalid index.",
                "Expected an end index in range [start = ${start}, ${instance.length}), but received ${end}.",
            ) }
            Object(type("String"), instance.substring(start.intValue(), end.intValue()))
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
            Object(type("Integer"), BigInteger.fromInt(instance.compareTo(other)))
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

}
