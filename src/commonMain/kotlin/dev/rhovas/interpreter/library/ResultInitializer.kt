package dev.rhovas.interpreter.library

import dev.rhovas.interpreter.EVALUATOR
import dev.rhovas.interpreter.environment.Object
import dev.rhovas.interpreter.environment.Type
import dev.rhovas.interpreter.environment.Variable
import dev.rhovas.interpreter.evaluator.Evaluator

object ResultInitializer : Library.TypeInitializer("Result") {

    override fun initialize() {
        generics.add(generic("T"))
        generics.add(generic("E"))
        inherits.add(Type.HASHABLE[Type.RESULT[Type.EQUATABLE[generic("T", Type.EQUATABLE[generic("T")])], Type.EQUATABLE[generic("E", Type.EQUATABLE[generic("E")])]]])

        method("value",
            returns = Type.NULLABLE[generic("T")],
        ) { (instance) ->
            val instance = instance.value as Pair<Object?, Object?>?
            Object(Type.NULLABLE[instance?.first?.type ?: Type.DYNAMIC], instance?.first?.let { Pair(it, null) })
        }

        method("value!",
            returns = generic("T"),
        ) { (instance) ->
            val instance = instance.value as Pair<Object?, Object?>?
            instance?.first ?: throw Evaluator.Throw(instance?.second ?: Object(Type.EXCEPTION, "Invalid null access."))
        }

        method("error",
            returns = Type.NULLABLE[generic("E")],
        ) { (instance) ->
            val instance = instance.value as Pair<Object?, Object?>?
            Object(Type.NULLABLE[instance?.first?.type ?: Type.DYNAMIC], instance?.second?.let { Pair(it, null) })
        }

        method("map",
            generics = listOf(generic("R")),
            parameters = listOf("lambda" to Type.LAMBDA[Type.TUPLE[listOf(generic("T"))], generic("R"), Type.DYNAMIC]),
            returns = Type.RESULT[generic("R"), generic("E")],
        ) { (instance, lambda) ->
            val returnsType = lambda.type.generic("R", Type.LAMBDA.GENERIC)!!
            val instance = instance.value as Pair<Object?, Object?>?
            val lambda = lambda.value as Evaluator.Lambda
            val value = instance?.takeIf { instance.first != null }?.let {
                Pair(lambda.invoke(listOf(it.first!!), returnsType), null as Object?)
            }
            Object(Type.RESULT[value?.first?.type ?: returnsType, value?.second?.type ?: Type.EXCEPTION], value)
        }

        method("or",
            parameters = listOf("lambda" to Type.LAMBDA[Type.TUPLE[listOf()], Type.RESULT[generic("T"), generic("E")], Type.DYNAMIC]),
            returns = Type.RESULT[generic("T"), generic("E")],
        ) { (instance, lambda) ->
            val returnsType = lambda.type.generic("R", Type.LAMBDA.GENERIC)!!
            val instance = instance.value as Pair<Object?, Object?>?
            val lambda = lambda.value as Evaluator.Lambda
            val value = instance?.takeIf { instance.first != null } ?: run {
                lambda.invoke(listOf(), returnsType).value as Pair<Object?, Object?>?
            }
            Object(Type.RESULT[value?.first?.type ?: Type.DYNAMIC, value?.second?.type ?: Type.EXCEPTION], value)
        }

        method("else",
            parameters = listOf("lambda" to Type.LAMBDA[Type.TUPLE[listOf()], generic("T"), Type.DYNAMIC]),
            returns = generic("T"),
        ) { (instance, lambda) ->
            val returnsType = lambda.type.generic("R", Type.LAMBDA.GENERIC)!!
            val instance = instance.value as Pair<Object?, Object?>?
            val lambda = lambda.value as Evaluator.Lambda
            instance?.first ?: lambda.invoke(listOf(), returnsType)
        }

        method("equals", operator = "==",
            parameters = listOf("other" to Type.RESULT[generic("T"), generic("E")]),
            returns = Type.BOOLEAN,
        ) { (instance, other) ->
            val instance = instance.value as Pair<Object?, Object?>?
            val other = other.value as Pair<Object?, Object?>?
            val result = listOf(instance?.first to other?.first, instance?.second to other?.second).all { (instance, other) ->
                if (instance == null || other == null) instance == other else instance.methods.equals(other)
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
