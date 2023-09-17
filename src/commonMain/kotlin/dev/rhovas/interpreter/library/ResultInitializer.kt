package dev.rhovas.interpreter.library

import dev.rhovas.interpreter.environment.Component
import dev.rhovas.interpreter.environment.Modifiers
import dev.rhovas.interpreter.environment.Object
import dev.rhovas.interpreter.environment.Type
import dev.rhovas.interpreter.evaluator.Evaluator

object ResultInitializer : Library.ComponentInitializer(Component.Class("Result", Modifiers(Modifiers.Inheritance.VIRTUAL))) {

    override fun initialize() {
        generics.add(generic("T"))
        generics.add(generic("E"))
        inherits.add(Type.HASHABLE[Type.RESULT[Type.EQUATABLE[generic("T", Type.EQUATABLE[generic("T")])], Type.EQUATABLE[generic("E", Type.EQUATABLE[generic("E")])]]])

        method("value",
            parameters = listOf(),
            returns = Type.NULLABLE[generic("T")],
        ) { (instance): T1<Pair<Object?, Object?>?> ->
            Object(Type.NULLABLE[generics["T"]!!], instance?.first?.let { Pair(it, null) })
        }

        method("value!",
            parameters = listOf(),
            returns = generic("T"),
        ) { (instance): T1<Pair<Object?, Object?>?> ->
            instance?.first ?: throw Evaluator.Throw(instance?.second ?: Object(Type.EXCEPTION, "Invalid null access."))
        }

        method("error",
            parameters = listOf(),
            returns = Type.NULLABLE[generic("E")],
        ) { (instance): T1<Pair<Object?, Object?>?> ->
            Object(Type.NULLABLE[generics["E"]!!], instance?.second?.let { Pair(it, null) })
        }

        method("map",
            generics = listOf(generic("R")),
            parameters = listOf("lambda" to Type.LAMBDA[Type.TUPLE[listOf(generic("T"))], generic("R"), Type.DYNAMIC]),
            returns = Type.RESULT[generic("R"), generic("E")],
        ) { (instance, lambda): T2<Pair<Object?, Object?>?, Evaluator.Lambda> ->
            val value = instance?.takeIf { instance.first != null }?.let {
                Pair(lambda.invoke(listOf(it.first!!), generics["R"]!!), null as Object?)
            }
            Object(Type.RESULT[generics["R"]!!, generics["E"]!!], value)
        }

        method("or",
            parameters = listOf("lambda" to Type.LAMBDA[Type.TUPLE[listOf()], Type.RESULT[generic("T"), generic("E")], Type.DYNAMIC]),
            returns = Type.RESULT[generic("T"), generic("E")],
        ) { (instance, lambda): T2<Pair<Object?, Object?>?, Evaluator.Lambda> ->
            val returnsType = Type.RESULT[generics["T"]!!, generics["E"]!!]
            val value = instance?.takeIf { instance.first != null } ?: run {
                lambda.invoke(listOf(), returnsType).value as Pair<Object?, Object?>?
            }
            Object(returnsType, value)
        }

        method("else",
            parameters = listOf("lambda" to Type.LAMBDA[Type.TUPLE[listOf()], generic("T"), Type.DYNAMIC]),
            returns = generic("T"),
        ) { (instance, lambda): T2<Pair<Object?, Object?>?, Evaluator.Lambda> ->
            instance?.first ?: lambda.invoke(listOf(), generics["T"]!!)
        }

        method("equals", operator = "==",
            parameters = listOf("other" to Type.RESULT[generic("T"), generic("E")]),
            returns = Type.BOOLEAN,
        ) { (instance, other): T2<Pair<Object?, Object?>?, Pair<Object?, Object?>?> ->
            val result = listOf(instance?.first to other?.first, instance?.second to other?.second).all { (instance, other) ->
                if (instance == null || other == null) instance == other else instance.methods.equals(other)
            }
            Object(Type.BOOLEAN, result)
        }

        method("to",
            parameters = listOf("type" to Type.TYPE[Type.STRING]),
            returns = Type.STRING,
        ) { (instance, _type): T2<Pair<Object?, Object?>?, Type> ->
            when {
                instance?.first != null -> Object(Type.STRING, instance.first!!.methods.toString())
                instance?.second != null -> Object(Type.STRING, instance.second!!.methods.toString())
                else -> Object(Type.STRING, "null")
            }
        }
    }

}
