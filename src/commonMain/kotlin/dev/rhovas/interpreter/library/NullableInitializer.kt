package dev.rhovas.interpreter.library

import dev.rhovas.interpreter.EVALUATOR
import dev.rhovas.interpreter.environment.Object
import dev.rhovas.interpreter.environment.Type

object NullableInitializer : Library.TypeInitializer("Nullable") {

    override fun initialize() {
        generics.add(generic("T"))
        inherits.add(Type.ANY)

        function("",
            generics = listOf(generic("T")),
            parameters = listOf("value" to generic("T")),
            returns = Type.NULLABLE[generic("T")],
        ) { (value) ->
            Object(Type.NULLABLE[value.type], value)
        }

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
            parameters = listOf("other" to Type.NULLABLE[generic("T")]),
            returns = Type.BOOLEAN,
        ) { (instance, other) ->
            val instance = instance.value as Object?
            val other = other.value as Object?
            if (instance == null || other == null) {
                Object(Type.BOOLEAN, instance == other)
            } else {
                val method = instance.methods["==", listOf(instance.type)] ?: throw EVALUATOR.error(
                    null,
                    "Undefined method.",
                    "The method ${instance.type.base.name}.==(${instance.type}) is undefined.",
                )
                when {
                    other.type.isSubtypeOf(method.parameters[0].type) -> method.invoke(listOf(other))
                    else -> Object(Type.BOOLEAN, false)
                }
            }
        }

        method("to",
            parameters = listOf("type" to Type.TYPE[Type.STRING]),
            returns = Type.STRING,
        ) { (instance) ->
            val instance = instance.value as Object?
            Object(Type.STRING, instance?.methods?.toString() ?: "null")
        }
    }

}
