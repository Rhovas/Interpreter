package dev.rhovas.interpreter.library

import dev.rhovas.interpreter.environment.Object
import dev.rhovas.interpreter.environment.Type
import dev.rhovas.interpreter.evaluator.EvaluateException
import dev.rhovas.interpreter.evaluator.Evaluator

object LambdaInitializer : Library.TypeInitializer("Lambda") {

    @Reflect.Method("invoke", parameters = ["List"], returns = "Any")
    fun invoke(instance: Evaluator.Lambda, arguments: List<Object>): Object {
        if (instance.ast.parameters.size != arguments.size) {
            throw EvaluateException("Lambda requires ${instance.ast.parameters.size} parameter(s), received ${arguments.size}.")
        }
        return instance.invoke(arguments.indices.map {
            val parameter = instance.ast.parameters.getOrNull(it)
            Triple(parameter?.first ?: "val_${it}", parameter?.second?.let { instance.evaluator.visit(it).value as Type } ?: Library.TYPES["Any"]!!, arguments[it])
        }, Library.TYPES["Any"]!!)
    }

    @Reflect.Method("toString", returns = "String")
    fun toString(instance: Evaluator.Lambda): String {
        return "Lambda/${instance.ast.parameters.size}#${instance.hashCode()}"
    }

}
