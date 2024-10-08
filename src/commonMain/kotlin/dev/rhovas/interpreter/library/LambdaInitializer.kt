package dev.rhovas.interpreter.library

import dev.rhovas.interpreter.environment.Component
import dev.rhovas.interpreter.environment.Object
import dev.rhovas.interpreter.environment.type.Type
import dev.rhovas.interpreter.evaluator.Evaluator

object LambdaInitializer : Library.ComponentInitializer(Component.Class("Lambda")) {

    override fun declare() {
        generics.add(generic("T", Type.TUPLE.DYNAMIC))
        generics.add(generic("R"))
        generics.add(generic("E", Type.EXCEPTION))
        inherits.add(Type.ANY)
    }

    override fun define() {
        method("invoke",
            parameters = listOf("arguments" to generic("T", Type.TUPLE.DYNAMIC)),
            throws = listOf(generic("E", Type.EXCEPTION)),
            returns = generic("R"),
        ) { (instance, arguments): T2<Evaluator.Lambda, List<Object>> ->
            instance.invoke(arguments, generics["R"]!!)
        }

        method("to",
            parameters = listOf("type" to Type.TYPE[Type.STRING]),
            returns = Type.STRING,
        ) { (instance): T1<Evaluator.Lambda> ->
            Object(Type.STRING, "Lambda/${instance.ir.parameters.size}#${instance.hashCode()}")
        }
    }

}
