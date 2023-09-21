package dev.rhovas.interpreter.library

import dev.rhovas.interpreter.environment.Component
import dev.rhovas.interpreter.environment.Modifiers
import dev.rhovas.interpreter.environment.Object
import dev.rhovas.interpreter.environment.Type

object ExceptionInitializer : Library.ComponentInitializer(Component.Class("Exception", Modifiers(Modifiers.Inheritance.VIRTUAL))) {

    override fun declare() {
        inherits.add(Type.HASHABLE[Type.EXCEPTION])
    }

    override fun define() {
        function("",
            parameters = listOf("message" to Type.STRING),
            returns = Type.EXCEPTION,
        ) { (message): T1<String> ->
            Object(Type.EXCEPTION, message)
        }

        method("message",
            parameters = listOf(),
            returns = Type.STRING,
        ) { (instance): T1<String> ->
            Object(Type.STRING, instance)
        }
    }

}
