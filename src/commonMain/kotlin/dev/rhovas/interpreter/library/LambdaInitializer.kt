package dev.rhovas.interpreter.library

import dev.rhovas.interpreter.EVALUATOR
import dev.rhovas.interpreter.environment.Object
import dev.rhovas.interpreter.environment.Type
import dev.rhovas.interpreter.evaluator.Evaluator

object LambdaInitializer : Library.TypeInitializer("Lambda") {

    override fun initialize() {
        //TODO(#2): Argument/Exception Generics
        //generics.add(generic("T"))
        generics.add(generic("R"))
        inherits.add(Type.ANY)

        method("invoke",
            generics = listOf(generic("R")),
            parameters = listOf("arguments" to Type.LIST[Type.DYNAMIC]),
            returns = generic("R")
        ) { (instance, arguments) ->
            val instance = instance.value as Evaluator.Lambda
            val arguments = arguments.value as List<Object>
            EVALUATOR.require(arguments.size == instance.ast.parameters.size) {
                EVALUATOR.error(
                    instance.ast,
                    "Invalid lambda argument count.",
                    "Lambda requires arguments of size ${instance.ast.parameters.size}, but received ${arguments.size}.",
                )
            }
            instance.invoke(arguments.indices.map {
                val parameter = instance.ast.parameters.getOrNull(it)
                Triple(
                    parameter?.name ?: "val_${it}",
                    parameter?.type ?: Type.ANY,
                    arguments[it])
            }, Type.ANY)
        }

        method("toString",
            returns = Type.STRING,
        ) { (instance) ->
            val instance = instance.value as Evaluator.Lambda
            Object(Type.STRING, "Lambda/${instance.ast.parameters.size}#${instance.hashCode()}")
        }
    }

}
