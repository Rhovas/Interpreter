package dev.rhovas.interpreter.library

import com.ionspin.kotlin.bignum.integer.BigInteger
import dev.rhovas.interpreter.EVALUATOR
import dev.rhovas.interpreter.environment.Object
import dev.rhovas.interpreter.environment.Type
import dev.rhovas.interpreter.environment.Variable
import dev.rhovas.interpreter.evaluator.Evaluator

object ListInitializer : Library.TypeInitializer("List", Type.Component.CLASS) {

    override fun initialize() {
        generics.add(generic("T"))
        inherits.add(Type.ITERABLE[generic("T")])
        inherits.add(Type.EQUATABLE[Type.LIST.DYNAMIC])

        method("size",
            returns = Type.INTEGER,
        ) { (instance) ->
            val instance = instance.value as List<Object>
            Object(Type.INTEGER, BigInteger.fromInt(instance.size))
        }

        method("empty",
            returns = Type.BOOLEAN,
        ) { (instance) ->
            val instance = instance.value as List<Object>
            Object(Type.BOOLEAN, instance.isEmpty())
        }

        method("first",
            returns = Type.NULLABLE[generic("T")],
        ) { (instance) ->
            val elementType = instance.type.generic("T", Type.LIST.GENERIC)!!
            val instance = instance.value as List<Object>
            Object(Type.NULLABLE[elementType], instance.firstOrNull()?.let { Pair(it, null) })
        }

        method("last",
            returns = Type.NULLABLE[generic("T")],
        ) { (instance) ->
            val elementType = instance.type.generic("T", Type.LIST.GENERIC)!!
            val instance = instance.value as List<Object>
            Object(Type.NULLABLE[elementType], instance.lastOrNull()?.let { Pair(it, null) })
        }

        method("get", operator = "[]",
            parameters = listOf("index" to Type.INTEGER),
            returns = generic("T")
        ) { (instance, index) ->
            val instance = instance.value as List<Object>
            val index = index.value as BigInteger
            EVALUATOR.require(index >= BigInteger.ZERO && index < BigInteger.fromInt(instance.size)) { EVALUATOR.error(null,
                "Invalid list index.",
                "Expected an index in range [0, ${instance.size}), but received ${index}.",
            ) }
            instance[index.intValue()]
        }

        method("set", operator = "[]=",
            parameters = listOf("index" to Type.INTEGER, "value" to generic("T")),
        ) { (instance, index, value) ->
            val instance = instance.value as MutableList<Object>
            val index = index.value as BigInteger
            EVALUATOR.require(index >= BigInteger.ZERO && index < BigInteger.fromInt(instance.size)) { EVALUATOR.error(null,
                "Invalid list index.",
                "Expected an index in range [0, ${instance.size}), but received ${index}.",
            ) }
            instance[index.intValue()] = value
            Object(Type.VOID, null)
        }

        method("slice",
            parameters = listOf("start" to Type.INTEGER),
            returns = Type.LIST[generic("T")],
        ) { (instance, start) ->
            val elementType = instance.type.generic("T", Type.LIST.GENERIC)!!
            val instance = instance.value as List<Object>
            val start = start.value as BigInteger
            EVALUATOR.require(start >= BigInteger.ZERO && start <= BigInteger.fromInt(instance.size)) { EVALUATOR.error(null,
                "Invalid index.",
                "Expected a start index in range [0, ${instance.size}), but received ${start}.",
            ) }
            Object(Type.LIST[elementType], instance.subList(start.intValue(), instance.size))
        }

        method("slice", "[]",
            parameters = listOf("start" to Type.INTEGER, "end" to Type.INTEGER),
            returns = Type.LIST[generic("T")],
        ) { (instance, start, end) ->
            val elementType = instance.type.generic("T", Type.LIST.GENERIC)!!
            val instance = instance.value as List<Object>
            val start = start.value as BigInteger
            val end = end.value as BigInteger
            EVALUATOR.require(start >= BigInteger.ZERO && start <= BigInteger.fromInt(instance.size)) { EVALUATOR.error(null,
                "Invalid index.",
                "Expected a start index in range [0, ${instance.size}), but received ${start}.",
            ) }
            EVALUATOR.require(end >= start && end <= BigInteger.fromInt(instance.size)) { EVALUATOR.error(null,
                "Invalid index.",
                "Expected an end index in range [start = ${start}, ${instance.size}), but received ${end}.",
            ) }
            Object(Type.LIST[elementType], instance.subList(start.intValue(), end.intValue()))
        }

        method("contains",
            parameters = listOf("value" to generic("T")),
            returns = Type.BOOLEAN,
        ) { (instance, value) ->
            val instance = instance.value as List<Object>
            Object(Type.BOOLEAN, instance.any { it.methods.equals(value) })
        }

        method("indexOf",
            parameters = listOf("value" to generic("T")),
            returns = Type.NULLABLE[Type.INTEGER],
        ) { (instance, other) ->
            val instance = instance.value as List<Object>
            val result = instance.indexOfFirst { other.methods.equals(it) }.takeIf { it != -1 }?.let { BigInteger.fromInt(it) }
            Object(Type.NULLABLE[Type.INTEGER], result?.let { Pair(Object(Type.INTEGER, it), null) })
        }

        method("find",
            parameters = listOf("lambda" to Type.LAMBDA[Type.TUPLE[listOf(generic("T"))], Type.BOOLEAN, Type.DYNAMIC]),
            returns = generic("T"),
        ) { (instance, lambda) ->
            val elementType = instance.type.generic("T", Type.LIST.GENERIC)!!
            val instance = instance.value as List<Object>
            val lambda = lambda.value as Evaluator.Lambda
            Object(Type.LIST[elementType], instance.find { lambda.invoke(listOf(it), Type.BOOLEAN).value as Boolean })
        }

        method("concat", operator = "+",
            parameters = listOf("other" to Type.LIST[generic("T")]),
            returns = Type.LIST[generic("T")],
        ) { (instance, other) ->
            val type = instance.type
            val instance = instance.value as List<Object>
            val other = other.value as List<Object>
            Object(type, instance + other)
        }

        method("repeat", operator = "*",
            parameters = listOf("times" to Type.INTEGER),
            returns = Type.LIST[generic("T")],
        ) { (instance, times) ->
            val elementType = instance.type.generic("T", Type.LIST.GENERIC)!!
            val instance = instance.value as List<Object>
            val times = times.value as BigInteger
            Object(Type.LIST[elementType], (0 until times.intValue()).flatMap { instance })
        }

        method("reverse",
            returns = Type.LIST[generic("T")],
        ) { (instance) ->
            val elementType = instance.type.generic("T", Type.LIST.GENERIC)!!
            val instance = instance.value as List<Object>
            Object(Type.LIST[elementType], instance.reversed())
        }

        method("map",
            parameters = listOf("lambda" to Type.LAMBDA[Type.TUPLE[listOf(generic("T"))], generic("R"), Type.DYNAMIC]),
            returns = Type.LIST[generic("R")],
        ) { (instance, lambda) ->
            val resultType = lambda.type.generic("R", Type.LAMBDA.GENERIC)!!
            val instance = instance.value as List<Object>
            val lambda = lambda.value as Evaluator.Lambda
            Object(Type.LIST[resultType], instance.map { lambda.invoke(listOf(it), resultType) })
        }

        method("filter",
            parameters = listOf("lambda" to Type.LAMBDA[Type.TUPLE[listOf(generic("T"))], Type.BOOLEAN, Type.DYNAMIC]),
            returns = Type.LIST[generic("T")],
        ) { (instance, lambda) ->
            val elementType = instance.type.generic("T", Type.LIST.GENERIC)!!
            val instance = instance.value as List<Object>
            val lambda = lambda.value as Evaluator.Lambda
            Object(Type.LIST[elementType], instance.filter { lambda.invoke(listOf(it), Type.BOOLEAN).value as Boolean })
        }

        method("reduce",
            parameters = listOf("lambda" to Type.LAMBDA[Type.TUPLE[listOf(generic("T"), generic("T"))], generic("T"), Type.DYNAMIC]),
            returns = Type.NULLABLE[generic("T")],
        ) { (instance, lambda) ->
            val elementType = instance.type.generic("T", Type.LIST.GENERIC)!!
            val instance = instance.value as List<Object>
            val lambda = lambda.value as Evaluator.Lambda
            val result = instance.reduceOrNull { result, element -> lambda.invoke(listOf(result, element), elementType) }
            Object(Type.NULLABLE[elementType], result?.let { Pair(it, null) })
        }

        method("reduce",
            generics = listOf(generic("R")),
            parameters = listOf("initial" to generic("R"), "lambda" to Type.LAMBDA[Type.TUPLE[listOf(generic("T"), generic("T"))], generic("T"), Type.DYNAMIC]),
            returns = generic("R"),
        ) { (instance, initial, lambda) ->
            val resultType = lambda.type.generic("R", Type.LAMBDA.GENERIC)!!
            val instance = instance.value as List<Object>
            val lambda = lambda.value as Evaluator.Lambda
            instance.fold(initial) { result, element -> lambda.invoke(listOf(result, element), resultType) }
        }
    }

}
