package dev.rhovas.interpreter.library

import com.ionspin.kotlin.bignum.integer.BigInteger
import dev.rhovas.interpreter.EVALUATOR
import dev.rhovas.interpreter.environment.Object
import dev.rhovas.interpreter.environment.Type

object StringInitializer : Library.TypeInitializer("String") {

    override fun initialize() {
        inherits.add(Type.ANY)

        method("size",
            returns = Type.INTEGER,
        ) { (instance) ->
            val instance = instance.value as String
            Object(Type.INTEGER, BigInteger.fromInt(instance.length))
        }

        method("slice",
            parameters = listOf("start" to Type.INTEGER),
            returns = Type.LIST[generic("T")],
        ) { (instance, start) ->
            val instance = instance.value as String
            val start = start.value as BigInteger
            EVALUATOR.require(start >= BigInteger.ZERO && start <= BigInteger.fromInt(instance.length)) { EVALUATOR.error(
                null,
                "Invalid index.",
                "Expected a start index in range [0, ${instance.length}), but received ${start}.",
            ) }
            Object(Type.STRING, instance.substring(start.intValue()))
        }

        method("slice",
            parameters = listOf("start" to Type.INTEGER, "end" to Type.INTEGER),
            returns = Type.LIST[generic("T")],
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
            Object(Type.STRING, instance.substring(start.intValue(), end.intValue()))
        }

        method("contains",
            parameters = listOf("other" to Type.STRING),
            returns = Type.BOOLEAN,
        ) { (instance, other) ->
            val instance = instance.value as String
            val other = other.value as String
            Object(Type.BOOLEAN, instance.contains(other))
        }

        method("replace",
            parameters = listOf("original" to Type.STRING, "value" to Type.STRING),
            returns = Type.STRING,
        ) { (instance, original, value) ->
            val instance = instance.value as String
            val original = original.value as String
            val value = value.value as String
            Object(Type.STRING, instance.replace(original, value))
        }

        method("concat", operator = "+",
            parameters = listOf("other" to Type.STRING),
            returns = Type.STRING,
        ) { (instance, other) ->
            val instance = instance.value as String
            val other = other.value as String
            Object(Type.STRING, instance + other)
        }

        method("equals", operator = "==",
            parameters = listOf("other" to Type.STRING),
            returns = Type.BOOLEAN,
        ) { (instance, other) ->
            Object(Type.BOOLEAN, instance.value == other.value)
        }

        method("compare", operator = "<=>",
            parameters = listOf("other" to Type.STRING),
            returns = Type.INTEGER,
        ) { (instance, other) ->
            val instance = instance.value as String
            val other = other.value as String
            Object(Type.INTEGER, BigInteger.fromInt(instance.compareTo(other)))
        }

        method("to",
            parameters = listOf("type" to Type.TYPE[Type.STRING]),
            returns = Type.STRING,
        ) { (instance) ->
            Object(Type.STRING, "${instance.value}")
        }

        method("to",
            parameters = listOf("type" to Type.TYPE[Type.ATOM]),
            returns = Type.ATOM,
        ) { (instance) ->
            val instance = instance.value as String
            Object(Type.ATOM, instance)
        }
    }

}
