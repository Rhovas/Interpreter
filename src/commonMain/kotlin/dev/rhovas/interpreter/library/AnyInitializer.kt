package dev.rhovas.interpreter.library

import dev.rhovas.interpreter.environment.Modifiers
import dev.rhovas.interpreter.environment.Object
import dev.rhovas.interpreter.environment.Type
import dev.rhovas.interpreter.evaluator.Evaluator

object AnyInitializer: Library.TypeInitializer("Any", Type.Component.CLASS, Modifiers(Modifiers.Inheritance.ABSTRACT)) {

    override fun initialize() {
        function("do",
            generics = listOf(generic("T"), generic("R")),
            parameters = listOf("instance" to generic("T"), "lambda" to Type.LAMBDA[Type.TUPLE[listOf(generic("T"))], generic("R"), Type.DYNAMIC]),
            returns = generic("R"),
        ) { (instance, lambda) ->
            val returnsType = instance.type.generic("R", Type.LAMBDA.GENERIC)!!
            val lambda = lambda.value as Evaluator.Lambda
            lambda.invoke(listOf(instance), returnsType)
        }

        function("if",
            generics = listOf(generic("T"), generic("E")),
            parameters = listOf("instance" to generic("T"), "lambda" to Type.LAMBDA[Type.TUPLE[listOf(generic("T"))], Type.BOOLEAN, Type.DYNAMIC]),
            returns = Type.NULLABLE[generic("T")],
        ) { (instance, lambda) ->
            val lambda = lambda.value as Evaluator.Lambda
            val result = instance.takeIf { lambda.invoke(listOf(instance), Type.BOOLEAN).value as Boolean }
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
            Object(Type.NULLABLE[type], instance.takeIf { it.type.isSubtypeOf(type) }?.let { Pair(it, null) })
        }

        method("to",
            modifiers = Modifiers(Modifiers.Inheritance.VIRTUAL),
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
            val prefix = instance.type.base.name.takeIf { it != "Struct" && instance.value is Map<*, *> } ?: ""
            Object(Type.STRING, prefix + toString(instance.value))
        }
    }

}
