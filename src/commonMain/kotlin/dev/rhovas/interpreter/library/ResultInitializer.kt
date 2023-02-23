package dev.rhovas.interpreter.library

import dev.rhovas.interpreter.EVALUATOR
import dev.rhovas.interpreter.environment.Object
import dev.rhovas.interpreter.environment.Type
import dev.rhovas.interpreter.evaluator.Evaluator

object ResultInitializer : Library.TypeInitializer("Result") {

    override fun initialize() {
        generics.add(generic("T"))
        generics.add(generic("E"))
        inherits.add(Type.ANY)

        method("value",
            returns = Type.NULLABLE[generic("T")],
        ) { (instance) ->
            val instance = instance.value as Pair<Object?, Object?>?
            Object(Type.NULLABLE[instance?.first?.type ?: Type.DYNAMIC], instance?.first?.let { Pair(it, null) })
        }

        method("error",
            returns = Type.NULLABLE[generic("E")],
        ) { (instance) ->
            val instance = instance.value as Pair<Object?, Object?>?
            Object(Type.NULLABLE[instance?.first?.type ?: Type.DYNAMIC], instance?.second?.let { Pair(it, null) })
        }

        method("get",
            returns = generic("T"),
        ) { (instance) ->
            val instance = instance.value as Pair<Object?, Object?>?
            instance?.first ?: throw EVALUATOR.error(
                null,
                "Invalid null access",
                "The Result value is null${instance?.second?.let { "(${it.methods.toString()})" } ?: ""}.",
            )
        }

        method("map",
            generics = listOf(generic("R")),
            parameters = listOf("lambda" to Type.LAMBDA[generic("R")]),
            returns = Type.RESULT[generic("R"), generic("E")],
        ) { (instance, lambda) ->
            val valueType = instance.type.methods["get", listOf()]!!.returns
            val returnsType = lambda.type.methods["invoke", listOf(Type.LIST.ANY)]!!.returns
            val instance = instance.value as Pair<Object?, Object?>?
            val lambda = lambda.value as Evaluator.Lambda
            EVALUATOR.require(lambda.ast.parameters.isEmpty() || lambda.ast.parameters.size == 1) { EVALUATOR.error(lambda.ast,
                "Invalid lambda parameter count.",
                "The function Result.map requires a lambda with 1 parameter, but received ${lambda.ast.parameters.size}.",
            ) }
            val value = instance?.takeIf { instance.first != null }?.let {
                Pair(lambda.invoke(listOf(Triple("value", valueType, it.first!!)), returnsType), null as Object?)
            }
            Object(Type.RESULT[value?.first?.type ?: Type.DYNAMIC, value?.second?.type ?: Type.EXCEPTION], value)
        }

        method("or",
            parameters = listOf("lambda" to Type.LAMBDA[Type.RESULT[generic("T"), generic("E")]]),
            returns = Type.RESULT[generic("T"), generic("E")],
        ) { (instance, lambda) ->
            val returnsType = lambda.type.methods["invoke", listOf(Type.LIST.ANY)]!!.returns
            val instance = instance.value as Pair<Object?, Object?>?
            val lambda = lambda.value as Evaluator.Lambda
            EVALUATOR.require(lambda.ast.parameters.isEmpty()) { EVALUATOR.error(lambda.ast,
                "Invalid lambda parameter count.",
                "The function Result.or requires a lambda with 0 parameters, but received ${lambda.ast.parameters.size}.",
            ) }
            val value = instance?.takeIf { instance.first != null } ?: run {
                lambda.invoke(listOf(), returnsType).value as Pair<Object?, Object?>?
            }
            Object(Type.RESULT[value?.first?.type ?: Type.DYNAMIC, value?.second?.type ?: Type.EXCEPTION], value)
        }

        method("else",
            parameters = listOf("lambda" to Type.LAMBDA[generic("T")]),
            returns = generic("T"),
        ) { (instance, lambda) ->
            val lambdaType = lambda.type.methods["invoke", listOf(Type.LIST.ANY)]!!.returns
            val instance = instance.value as Pair<Object?, Object?>?
            val lambda = lambda.value as Evaluator.Lambda
            EVALUATOR.require(lambda.ast.parameters.isEmpty()) { EVALUATOR.error(lambda.ast,
                "Invalid lambda parameter count.",
                "The function Result.else requires a lambda with 0 parameters, but received ${lambda.ast.parameters.size}.",
            ) }
            instance?.first ?: lambda.invoke(listOf(), lambdaType)
        }

        method("equals", operator = "==",
            parameters = listOf("other" to Type.RESULT[generic("T"), generic("E")]),
            returns = Type.BOOLEAN,
        ) { (instance, other) ->
            val instance = instance.value as Pair<Object?, Object?>?
            val other = other.value as Pair<Object?, Object?>?
            val result = listOf(instance?.first to other?.first, instance?.second to other?.second).all { (instance, other) ->
                if (instance == null || other == null) instance == other else {
                    val method = instance.methods["==", listOf(instance.type)] ?: throw EVALUATOR.error(
                        null,
                        "Undefined method.",
                        "The method ${instance.type.base.name}.==(${instance.type}) is undefined.",
                    )
                    if (other.type.isSubtypeOf(method.parameters[0].type)) method.invoke(listOf(other)).value as Boolean else false
                }
            }
            Object(Type.BOOLEAN, result)
        }

        method("to",
            parameters = listOf("type" to Type.TYPE[Type.STRING]),
            returns = Type.STRING,
        ) { (instance) ->
            val instance = instance.value as Pair<Object?, Object?>?
            when {
                instance?.first != null -> Object(Type.STRING, instance.first!!.methods.toString())
                instance?.second != null -> Object(Type.STRING, instance.second!!.methods.toString())
                else -> Object(Type.STRING, "null")
            }
        }
    }

}
