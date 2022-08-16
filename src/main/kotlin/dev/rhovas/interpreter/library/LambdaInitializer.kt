package dev.rhovas.interpreter.library

import dev.rhovas.interpreter.EVALUATOR
import dev.rhovas.interpreter.environment.Object
import dev.rhovas.interpreter.environment.Type
import dev.rhovas.interpreter.evaluator.Evaluator

object LambdaInitializer : Library.TypeInitializer("Lambda") {

    override fun initialize() {
        //TODO: Argument Generics
        //generics.add(generic("T"))
        generics.add(generic("R"))
        inherits.add(type("Any"))

        method("invoke",
            generics = listOf(generic("R")),
            parameters = listOf("arguments" to type("List", "Dynamic")),
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
                    parameter?.first ?: "val_${it}",
                    parameter?.second?.let { instance.evaluator.visit(it).value as Type } ?: Library.TYPES["Any"]!!,
                    arguments[it])
            }, Library.TYPES["Any"]!!)
        }

        method("toString",
            returns = type("String"),
        ) { (instance) ->
            val instance = instance.value as Evaluator.Lambda
            Object(type("String"), "Lambda/${instance.ast.parameters.size}#${instance.hashCode()}")
        }
    }

}
