package dev.rhovas.interpreter.library

import com.ionspin.kotlin.bignum.integer.BigInteger
import dev.rhovas.interpreter.environment.Component
import dev.rhovas.interpreter.environment.Object
import dev.rhovas.interpreter.environment.Type
import dev.rhovas.interpreter.evaluator.Evaluator

object ListInitializer : Library.ComponentInitializer(Component.Class("List")) {

    override fun initialize() {
        generics.add(generic("T"))
        inherits.add(Type.ITERABLE[generic("T")])
        inherits.add(Type.EQUATABLE[Type.LIST.DYNAMIC])

        method("size",
            parameters = listOf(),
            returns = Type.INTEGER,
        ) { (instance): T1<List<Object>> ->
            Object(Type.INTEGER, BigInteger.fromInt(instance.size))
        }

        method("empty",
            parameters = listOf(),
            returns = Type.BOOLEAN,
        ) { (instance): T1<List<Object>> ->
            Object(Type.BOOLEAN, instance.isEmpty())
        }

        method("first",
            parameters = listOf(),
            returns = Type.NULLABLE[generic("T")],
        ) { (instance): T1<List<Object>> ->
            Object(Type.NULLABLE[generics["T"]!!], instance.firstOrNull()?.let { Pair(it, null) })
        }

        method("last",
            parameters = listOf(),
            returns = Type.NULLABLE[generic("T")],
        ) { (instance): T1<List<Object>> ->
            Object(Type.NULLABLE[generics["T"]!!], instance.lastOrNull()?.let { Pair(it, null) })
        }

        method("get", operator = "[]",
            parameters = listOf("index" to Type.INTEGER),
            returns = generic("T")
        ) { (instance, index): T2<List<Object>, BigInteger> ->
            require(index >= BigInteger.ZERO && index < BigInteger.fromInt(instance.size)) { error(
                "Invalid list index.",
                "Expected an index in range [0, ${instance.size}), but received ${index}.",
            ) }
            instance[index.intValue()]
        }

        method("set", operator = "[]=",
            parameters = listOf("index" to Type.INTEGER, "value" to generic("T")),
            returns = Type.VOID,
        ) { (instance, index, value): T3<MutableList<Object>, BigInteger, Object> ->
            require(index >= BigInteger.ZERO && index < BigInteger.fromInt(instance.size)) { error(
                "Invalid list index.",
                "Expected an index in range [0, ${instance.size}), but received ${index}.",
            ) }
            instance[index.intValue()] = value
            Object(Type.VOID, null)
        }

        method("slice",
            parameters = listOf("start" to Type.INTEGER),
            returns = Type.LIST[generic("T")],
        ) { (instance, start): T2<List<Object>, BigInteger> ->
            require(start >= BigInteger.ZERO && start <= BigInteger.fromInt(instance.size)) { error(
                "Invalid index.",
                "Expected a start index in range [0, ${instance.size}), but received ${start}.",
            ) }
            Object(Type.LIST[generics["T"]!!], instance.subList(start.intValue(), instance.size))
        }

        method("slice", "[]",
            parameters = listOf("start" to Type.INTEGER, "end" to Type.INTEGER),
            returns = Type.LIST[generic("T")],
        ) { (instance, start, end): T3<List<Object>, BigInteger, BigInteger> ->
            require(start >= BigInteger.ZERO && start <= BigInteger.fromInt(instance.size)) { error(
                "Invalid index.",
                "Expected a start index in range [0, ${instance.size}), but received ${start}.",
            ) }
            require(end >= start && end <= BigInteger.fromInt(instance.size)) { error(
                "Invalid index.",
                "Expected an end index in range [start = ${start}, ${instance.size}), but received ${end}.",
            ) }
            Object(Type.LIST[generics["T"]!!], instance.subList(start.intValue(), end.intValue()))
        }

        method("contains",
            parameters = listOf("value" to generic("T")),
            returns = Type.BOOLEAN,
        ) { (instance, value): T2<List<Object>, Object> ->
            Object(Type.BOOLEAN, instance.any { it.methods.equals(value) })
        }

        method("indexOf",
            parameters = listOf("value" to generic("T")),
            returns = Type.NULLABLE[Type.INTEGER],
        ) { (instance, other): T2<List<Object>, Object> ->
            val result = instance.indexOfFirst { other.methods.equals(it) }.takeIf { it != -1 }?.let { BigInteger.fromInt(it) }
            Object(Type.NULLABLE[Type.INTEGER], result?.let { Pair(Object(Type.INTEGER, it), null) })
        }

        method("find",
            parameters = listOf("lambda" to Type.LAMBDA[Type.TUPLE[listOf(generic("T"))], Type.BOOLEAN, Type.DYNAMIC]),
            returns = generic("T"),
        ) { (instance, lambda): T2<List<Object>, Evaluator.Lambda> ->
            Object(generics["T"]!!, instance.find { lambda.invoke(listOf(it), Type.BOOLEAN).value as Boolean })
        }

        method("concat", operator = "+",
            parameters = listOf("other" to Type.LIST[generic("T")]),
            returns = Type.LIST[generic("T")],
        ) { (instance, other): T2<List<Object>, List<Object>> ->
            Object(Type.LIST[generics["T"]!!], instance + other)
        }

        method("repeat", operator = "*",
            parameters = listOf("times" to Type.INTEGER),
            returns = Type.LIST[generic("T")],
        ) { (instance, times): T2<List<Object>, BigInteger> ->
            Object(Type.LIST[generics["T"]!!], (0 until times.intValue()).flatMap { instance })
        }

        method("reverse",
            parameters = listOf(),
            returns = Type.LIST[generic("T")],
        ) { (instance): T1<List<Object>> ->
            Object(Type.LIST[generics["T"]!!], instance.reversed())
        }

        method("map",
            parameters = listOf("lambda" to Type.LAMBDA[Type.TUPLE[listOf(generic("T"))], generic("R"), Type.DYNAMIC]),
            returns = Type.LIST[generic("R")],
        ) { (instance, lambda): T2<List<Object>, Evaluator.Lambda> ->
            Object(Type.LIST[generics["R"]!!], instance.map { lambda.invoke(listOf(it), generics["R"]!!) })
        }

        method("filter",
            parameters = listOf("lambda" to Type.LAMBDA[Type.TUPLE[listOf(generic("T"))], Type.BOOLEAN, Type.DYNAMIC]),
            returns = Type.LIST[generic("T")],
        ) { (instance, lambda): T2<List<Object>, Evaluator.Lambda> ->
            Object(Type.LIST[generics["T"]!!], instance.filter { lambda.invoke(listOf(it), Type.BOOLEAN).value as Boolean })
        }

        method("reduce",
            parameters = listOf("lambda" to Type.LAMBDA[Type.TUPLE[listOf(generic("T"), generic("T"))], generic("T"), Type.DYNAMIC]),
            returns = Type.NULLABLE[generic("T")],
        ) { (instance, lambda): T2<List<Object>, Evaluator.Lambda> ->
            val result = instance.reduceOrNull { result, element -> lambda.invoke(listOf(result, element), generics["T"]!!) }
            Object(Type.NULLABLE[generics["T"]!!], result?.let { Pair(it, null) })
        }

        method("reduce",
            generics = listOf(generic("R")),
            parameters = listOf("initial" to generic("R"), "lambda" to Type.LAMBDA[Type.TUPLE[listOf(generic("T"), generic("T"))], generic("T"), Type.DYNAMIC]),
            returns = generic("R"),
        ) { (instance, initial, lambda): T3<List<Object>, Object, Evaluator.Lambda> ->
            instance.fold(initial) { result, element -> lambda.invoke(listOf(result, element), generics["R"]!!) }
        }
    }

}
