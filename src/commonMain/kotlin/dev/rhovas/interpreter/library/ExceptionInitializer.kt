package dev.rhovas.interpreter.library

import dev.rhovas.interpreter.environment.Object
import dev.rhovas.interpreter.environment.Type

object ExceptionInitializer : Library.TypeInitializer("Exception") {

    override fun initialize() {
        inherits.add(Type.EQUATABLE[Type.EXCEPTION])

        function("",
            parameters = listOf("message" to Type.STRING),
            returns = Type.EXCEPTION,
        ) { (message) ->
            val message = message.value as String
            Object(Type.EXCEPTION, message)
        }

        method("message",
            returns = Type.STRING,
        ) { (instance) ->
            Object(Type.STRING, "${instance.value}")
        }
    }

}
