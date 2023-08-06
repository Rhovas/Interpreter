package dev.rhovas.interpreter.library

import dev.rhovas.interpreter.environment.Component
import dev.rhovas.interpreter.environment.Modifiers
import dev.rhovas.interpreter.environment.Object
import dev.rhovas.interpreter.environment.Scope
import dev.rhovas.interpreter.environment.Type
import dev.rhovas.interpreter.evaluator.Evaluator

object LambdaInitializer : Library.ComponentInitializer(Component.Class("Lambda", Modifiers(Modifiers.Inheritance.DEFAULT), Scope.Definition(null))) {

    override fun initialize() {
        generics.add(generic("T", Type.TUPLE.DYNAMIC))
        generics.add(generic("R"))
        generics.add(generic("E", Type.EXCEPTION))
        inherits.add(Type.ANY)

        method("invoke",
            parameters = listOf("arguments" to generic("T", Type.TUPLE.DYNAMIC)),
            throws = listOf(generic("E", Type.EXCEPTION)),
            returns = generic("R"),
        ) { (instance, arguments) ->
            val returnsType = instance.type.generic("R", Type.LAMBDA.GENERIC)!!
            val instance = instance.value as Evaluator.Lambda
            val arguments = arguments.value as List<Object>
            instance.invoke(arguments, returnsType)
        }

        method("to",
            parameters = listOf("type" to Type.TYPE[Type.STRING]),
            returns = Type.STRING,
        ) { (instance) ->
            val instance = instance.value as Evaluator.Lambda
            Object(Type.STRING, "Lambda/${instance.ir.parameters.size}#${instance.hashCode()}")
        }
    }

}
