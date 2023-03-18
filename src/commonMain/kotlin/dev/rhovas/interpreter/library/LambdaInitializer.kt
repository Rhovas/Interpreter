package dev.rhovas.interpreter.library

import dev.rhovas.interpreter.EVALUATOR
import dev.rhovas.interpreter.environment.Object
import dev.rhovas.interpreter.environment.Type
import dev.rhovas.interpreter.evaluator.Evaluator

object LambdaInitializer : Library.TypeInitializer("Lambda") {

    override fun initialize() {
        generics.add(generic("T", Type.TUPLE.ANY))
        generics.add(generic("R"))
        generics.add(generic("E", Type.EXCEPTION))
        inherits.add(Type.ANY)

        method("invoke",
            parameters = listOf("arguments" to generic("T")),
            throws = listOf(generic("E")),
            returns = generic("R")
        ) { (instance, arguments) ->
            val argumentsType = ((instance.type as Type.Reference).generics[0] as? Type.Reference)?.generics?.first() as? Type.Tuple
            val returnsType = (instance.type as Type.Reference).generics[1]
            val instance = instance.value as Evaluator.Lambda
            val arguments = arguments.value as List<Object>
            EVALUATOR.require(arguments.size == instance.ast.parameters.size) { EVALUATOR.error(
                instance.ast,
                "Invalid lambda argument count.",
                "Lambda requires arguments of size ${instance.ast.parameters.size}, but received ${arguments.size}.",
            ) }
            instance.invoke(arguments.indices.map {
                val parameter = instance.ast.parameters.getOrNull(it)
                val element = argumentsType?.elements?.get(it)
                Triple(parameter?.name ?: element?.name ?: "val_${it}", parameter?.type ?: element?.type ?: Type.ANY, arguments[it])
            }, returnsType)
        }

        method("to",
            parameters = listOf("type" to Type.TYPE[Type.STRING]),
            returns = Type.STRING,
        ) { (instance) ->
            val instance = instance.value as Evaluator.Lambda
            Object(Type.STRING, "Lambda/${instance.ast.parameters.size}#${instance.hashCode()}")
        }
    }

}
