package dev.rhovas.interpreter.library

import com.ionspin.kotlin.bignum.integer.BigInteger
import dev.rhovas.interpreter.EVALUATOR
import dev.rhovas.interpreter.environment.Object
import dev.rhovas.interpreter.environment.Type
import dev.rhovas.interpreter.evaluator.Evaluator

object ListInitializer : Library.TypeInitializer("List") {

    override fun initialize() {
        generics.add(generic("T"))
        inherits.add(Type.ANY)

        method("size",
            returns = Type.INTEGER,
        ) { (instance) ->
            val instance = instance.value as List<Object>
            Object(Type.INTEGER, BigInteger.fromInt(instance.size))
        }

        method("first",
            returns = Type.NULLABLE[generic("T")],
        ) { (instance) ->
            val instance = instance.value as List<Object>
            instance.firstOrNull() ?: Object(Type.NULL, null)
        }

        method("last",
            returns = Type.NULLABLE[generic("T")],
        ) { (instance) ->
            val instance = instance.value as List<Object>
            instance.lastOrNull() ?: Object(Type.NULL, null)
        }

        method("get", operator = "[]",
            parameters = listOf("index" to Type.INTEGER),
            returns = generic("T")
        ) { (instance, index) ->
            val instance = instance.value as List<Object>
            val index = index.value as BigInteger
            EVALUATOR.require(index >= BigInteger.ZERO && index < BigInteger.fromInt(instance.size)) { EVALUATOR.error(
                null,
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
            EVALUATOR.require(index >= BigInteger.ZERO && index < BigInteger.fromInt(instance.size)) { EVALUATOR.error(
                null,
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
            val elementType = instance.type.methods["get", listOf(Type.INTEGER)]!!.returns
            val instance = instance.value as List<Object>
            val start = start.value as BigInteger
            EVALUATOR.require(start >= BigInteger.ZERO && start <= BigInteger.fromInt(instance.size)) { EVALUATOR.error(
                null,
                "Invalid index.",
                "Expected a start index in range [0, ${instance.size}), but received ${start}.",
            ) }
            Object(Type.LIST[elementType], instance.subList(start.intValue(), instance.size))
        }

        method("slice",
            parameters = listOf("start" to Type.INTEGER, "end" to Type.INTEGER),
            returns = Type.LIST[generic("T")],
        ) { (instance, start, end) ->
            val elementType = instance.type.methods["get", listOf(Type.INTEGER)]!!.returns
            val instance = instance.value as List<Object>
            val start = start.value as BigInteger
            val end = end.value as BigInteger
            EVALUATOR.require(start >= BigInteger.ZERO && start <= BigInteger.fromInt(instance.size)) { EVALUATOR.error(
                null,
                "Invalid index.",
                "Expected a start index in range [0, ${instance.size}), but received ${start}.",
            ) }
            EVALUATOR.require(end >= start && end <= BigInteger.fromInt(instance.size)) { EVALUATOR.error(
                null,
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
            val method = value.methods["==", listOf(value.type)] ?: throw EVALUATOR.error(
                null,
                "Undefined method.",
                "The method ${value.type.base.name}.==(${value.type}) is undefined.",
            )
            Object(Type.BOOLEAN, instance.any {
                it.type.isSubtypeOf(method.parameters[0].type) && method.invoke(listOf(it)).value as Boolean
            })
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

        method("for",
            parameters = listOf("lambda" to Type.LAMBDA[Type.VOID]),
        ) { (instance, lambda) ->
            val elementType = instance.type.methods["get", listOf(Type.INTEGER)]!!.returns
            val instance = instance.value as List<Object>
            val lambda = lambda.value as Evaluator.Lambda
            EVALUATOR.require(lambda.ast.parameters.isEmpty() || lambda.ast.parameters.size == 1) { EVALUATOR.error(
                lambda.ast,
                "Invalid lambda parameter count.",
                "Function List.for requires a lambda with 1 parameter, but received ${lambda.ast.parameters.size}.",
            ) }
            instance.forEach {
                lambda.invoke(listOf(Triple("val", elementType, it)), Type.VOID)
            }
            Object(Type.VOID, null)
        }

        method("map",
            parameters = listOf("lambda" to Type.LAMBDA[generic("R")]),
            returns = Type.LIST[generic("R")],
        ) { (instance, lambda) ->
            val elementType = instance.type.methods["get", listOf(Type.INTEGER)]!!.returns
            val resultType = lambda.type.methods["invoke", listOf(Type.LIST[Type.DYNAMIC])]!!.returns
            val instance = instance.value as List<Object>
            val lambda = lambda.value as Evaluator.Lambda
            EVALUATOR.require(lambda.ast.parameters.isEmpty() || lambda.ast.parameters.size == 1) { EVALUATOR.error(
                lambda.ast,
                "Invalid lambda parameter count.",
                "Function List.map requires a lambda with 1 parameter, but received ${lambda.ast.parameters.size}.",
            ) }
            Object(Type.LIST[elementType], instance.map {
                lambda.invoke(listOf(Triple("val", elementType, it)), resultType)
            })
        }

        method("filter",
            parameters = listOf("lambda" to Type.LAMBDA[Type.BOOLEAN]),
            returns = Type.LIST[generic("T")],
        ) { (instance, lambda) ->
            val elementType = instance.type.methods["get", listOf(Type.INTEGER)]!!.returns
            val instance = instance.value as List<Object>
            val lambda = lambda.value as Evaluator.Lambda
            EVALUATOR.require(lambda.ast.parameters.isEmpty() || lambda.ast.parameters.size == 1) { EVALUATOR.error(
                lambda.ast,
                "Invalid lambda parameter count.",
                "Function List.map requires a lambda with 1 parameter, but received ${lambda.ast.parameters.size}.",
            ) }
            Object(Type.LIST[elementType], instance.filter {
                val result = lambda.invoke(listOf(Triple("val", elementType, it)), elementType)
                EVALUATOR.require(result.type.isSubtypeOf(Type.BOOLEAN)) { EVALUATOR.error(
                    lambda.ast,
                    "Invalid lambda result.",
                    "Function List.filter requires the lambda result to be type Boolean, but received ${result.type}.",
                ) }
                result.value as Boolean
            })
        }

        method("reduce",
            parameters = listOf("lambda" to Type.LAMBDA[generic("T")]),
            returns = Type.NULLABLE[generic("T")],
        ) { (instance, lambda) ->
            val elementType = instance.type.methods["get", listOf(Type.INTEGER)]!!.returns
            val resultType = lambda.type.methods["invoke", listOf(Type.LIST[Type.DYNAMIC])]!!.returns
            val instance = instance.value as List<Object>
            val lambda = lambda.value as Evaluator.Lambda
            EVALUATOR.require(lambda.ast.parameters.isEmpty() || lambda.ast.parameters.size == 2) { EVALUATOR.error(
                lambda.ast,
                "Invalid lambda parameter count.",
                "Function List.reduce requires a lambda with 2 parameters, but received ${lambda.ast.parameters.size}.",
            ) }
            instance.reduceOrNull { result, element ->
                lambda.invoke(listOf(
                    Triple("result", resultType, result),
                    Triple("element", elementType, element),
                ), resultType)
            } ?: Object(Type.NULL, null)
        }

        method("reduce",
            generics = listOf(generic("R")),
            parameters = listOf("initial" to generic("R"), "lambda" to Type.LAMBDA[generic("R")]),
            returns = generic("R"),
        ) { (instance, initial, lambda) ->
            val elementType = instance.type.methods["get", listOf(Type.INTEGER)]!!.returns
            val resultType = lambda.type.methods["invoke", listOf(Type.LIST[Type.DYNAMIC])]!!.returns
            val instance = instance.value as List<Object>
            val lambda = lambda.value as Evaluator.Lambda
            EVALUATOR.require(lambda.ast.parameters.isEmpty() || lambda.ast.parameters.size == 2) { EVALUATOR.error(
                lambda.ast,
                "Invalid lambda parameter count.",
                "Function List.reduce requires a lambda with 2 parameters, but received ${lambda.ast.parameters.size}.",
            ) }
            instance.fold(initial) { result, element ->
                lambda.invoke(listOf(
                    Triple("result", resultType, result),
                    Triple("element", elementType, element),
                ), resultType)
            }
        }

        method("equals", operator = "==",
            parameters = listOf("other" to Type.LIST[generic("T")]),
            returns = Type.BOOLEAN,
        ) { (instance, other) ->
            val instance = instance.value as List<Object>
            val other = other.value as List<Object>
            Object(Type.BOOLEAN, instance.size == other.size && instance.zip(other).all {
                val method = it.first.methods["==", listOf(it.first.type)] ?: throw EVALUATOR.error(
                    null,
                    "Undefined method.",
                    "The method ${it.first.type.base.name}.==(${it.first.type}) is undefined.",
                )
                if (it.second.type.isSubtypeOf(method.parameters[0].type)) method.invoke(listOf(it.second)).value as Boolean else false
            })
        }

        method("to",
            parameters = listOf("type" to Type.TYPE[Type.STRING]),
            returns = Type.STRING,
        ) { (instance) ->
            val instance = instance.value as List<Object>
            Object(Type.STRING, instance.map { it.methods.toString() }.toString())
        }
    }

}
