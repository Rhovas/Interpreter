package dev.rhovas.interpreter.library

import dev.rhovas.interpreter.environment.Object
import dev.rhovas.interpreter.environment.Type
import dev.rhovas.interpreter.environment.Variable
import dev.rhovas.interpreter.evaluator.Evaluator

object AnyInitializer: Library.TypeInitializer("Any") {

    override fun initialize() {
        function("do",
            generics = listOf(generic("T"), generic("R")),
            parameters = listOf("instance" to generic("T"), "lambda" to Type.LAMBDA[Type.TUPLE[Type.Tuple(listOf(Variable.Declaration("instance", generic("T"), false)))], generic("R"), Type.DYNAMIC]),
            returns = generic("R"),
        ) { (instance, lambda) ->
            val returnsType = lambda.type.methods["invoke", listOf(Type.LIST.ANY)]!!.returns
            val lambda = lambda.value as Evaluator.Lambda
            lambda.invoke(listOf(Triple("instance", instance.type, instance)), returnsType)
        }

        function("if",
            generics = listOf(generic("T"), generic("E")),
            parameters = listOf("instance" to generic("T"), "lambda" to Type.LAMBDA[Type.TUPLE[Type.Tuple(listOf(Variable.Declaration("instance", generic("T"), false)))], Type.BOOLEAN, Type.DYNAMIC]),
            returns = Type.NULLABLE[generic("T")],
        ) { (instance, lambda) ->
            val lambda = lambda.value as Evaluator.Lambda
            val result = instance.takeIf { lambda.invoke(listOf(Triple("instance", instance.type, instance)), Type.BOOLEAN).value as Boolean }
            Object(Type.NULLABLE[instance.type], result?.let { Pair(it, null) })
        }

        method("is",
            parameters = listOf("type" to Type.TYPE[generic("T")]),
            returns = Type.BOOLEAN,
        ) { (instance, type) ->
            val type = type.value as Type
            Object(Type.BOOLEAN, instance.type.isSubtypeOf(type))
        }

        method("as",
            parameters = listOf("type" to Type.TYPE[generic("T")]),
            returns = Type.NULLABLE[generic("T")],
        ) { (instance, type) ->
            val type = type.value as Type
            when {
                instance.type.isSubtypeOf(type) -> instance
                else -> Object(Type.NULLABLE.ANY, null)
            }
        }

        method("to",
            parameters = listOf("type" to Type.TYPE[Type.STRING]),
            returns = Type.STRING,
        ) { (instance, _) ->
            fun toString(value: Any?): String {
                return when (value) {
                    is Object -> value.methods.toString()
                    is Map<*, *> -> value.mapValues { toString(it.value) }.toString()
                    is Collection<*> -> value.map { toString(it) }.toString()
                    else -> value.toString()
                }
            }
            Object(Type.STRING, toString(instance.value))
        }
    }

}
