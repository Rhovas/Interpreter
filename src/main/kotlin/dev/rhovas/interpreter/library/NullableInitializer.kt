package dev.rhovas.interpreter.library

import dev.rhovas.interpreter.EVALUATOR
import dev.rhovas.interpreter.environment.Object

object NullableInitializer : Library.TypeInitializer("Nullable") {

    override fun initialize() {
        generics.add(generic("T"))
        inherits.add(type("Any"))

        method("get",
            returns = generic("T"),
        ) { (instance) ->
            instance.value as Object? ?: throw EVALUATOR.error(
                null,
                "Invalid Nullable access",
                "The value was not defined.",
            )
        }

        method("equals", operator = "==",
            parameters = listOf("other" to type("Nullable", generic("T"))),
            returns = type("Boolean"),
        ) { (instance, other) ->
            val instance = instance.value as Object?
            val other = other.value as Object?
            if (instance == null || other == null) {
                Object(type("Boolean"), instance == other)
            } else {
                val method = instance.methods["==", listOf(instance.type)] ?: throw EVALUATOR.error(
                    null,
                    "Undefined method.",
                    "The method ${instance.type.base.name}.==(${instance.type}) is undefined.",
                )
                when {
                    other.type.isSubtypeOf(method.parameters[0].second) -> method.invoke(listOf(other))
                    else -> Object(type("Boolean"), false)
                }
            }
        }

        method("toString",
            returns = type("String"),
        ) { (instance) ->
            val instance = instance.value as Object?
            when (instance) {
                null -> Object(type("String"), "null")
                else -> instance.methods["toString", listOf()]!!.invoke(listOf())
            }
        }
    }

}
