package dev.rhovas.interpreter.library

import dev.rhovas.interpreter.EVALUATOR
import dev.rhovas.interpreter.environment.Object
import dev.rhovas.interpreter.environment.Type
import dev.rhovas.interpreter.environment.Variable
import dev.rhovas.interpreter.evaluator.Evaluator

object LambdaInitializer : Library.TypeInitializer("Lambda") {

    override fun initialize() {
        generics.add(generic("T", Type.TUPLE[Type.DYNAMIC]))
        generics.add(generic("R"))
        generics.add(generic("E", Type.EXCEPTION))
        inherits.add(Type.ANY)

        method("invoke",
            parameters = listOf("arguments" to generic("T", Type.TUPLE[Type.DYNAMIC])),
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
