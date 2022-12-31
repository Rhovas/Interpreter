package dev.rhovas.interpreter.library

import dev.rhovas.interpreter.environment.Object
import dev.rhovas.interpreter.environment.Type

object AnyInitializer: Library.TypeInitializer("Any") {

    override fun initialize() {
        method("is",
            parameters = listOf("type" to type("Type")),
            returns = type("Boolean"),
        ) { (instance, type) ->
            val type = type.value as Type
            Object(type("Boolean"), instance.type.isSubtypeOf(type))
        }

        method("as",
            parameters = listOf("type" to type("Type", generic("T"))),
            returns = type("Nullable", generic("T")),
        ) { (instance, type) ->
            val type = type.value as Type
            when {
                instance.type.isSubtypeOf(type) -> instance
                else -> Object(type("Null"), null)
            }
        }

        method("to",
            parameters = listOf("type" to type("Type", type("String"))),
            returns = type("String"),
        ) { (instance, _) ->
            instance.methods["toString", listOf()]!!.invoke(listOf())
        }

        //TODO: Support overriding methods with inheritance
        /*method("toString",
            returns = type("String"),
        ) { (instance) ->
            Object(type("String"), "${instance.value}")
        }*/
    }

}
