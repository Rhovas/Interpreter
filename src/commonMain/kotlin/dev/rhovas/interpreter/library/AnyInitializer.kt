package dev.rhovas.interpreter.library

import dev.rhovas.interpreter.environment.Object
import dev.rhovas.interpreter.environment.Type

object AnyInitializer: Library.TypeInitializer("Any") {

    override fun initialize() {
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
            Object(Type.STRING, "${instance.value}")
        }
    }

}
