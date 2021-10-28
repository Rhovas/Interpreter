package dev.rhovas.interpreter.library

import dev.rhovas.interpreter.environment.Object
import dev.rhovas.interpreter.evaluator.EvaluateException
import dev.rhovas.interpreter.evaluator.Evaluator

object LambdaInitializer : Library.TypeInitializer("Lambda") {

    @Reflect.Method("invoke", parameters = ["List"], returns = "")
    fun invoke(instance: Evaluator.Lambda, arguments: List<Object>): Object {
        if (instance.ast.parameters.size != arguments.size) {
            throw EvaluateException("Lambda requires ${instance.ast.parameters.size} parameter(s), received ${arguments.size}.")
        }
        return instance.invoke(instance.ast.parameters.zip(arguments).associate { it.first.first to it.second })
    }

    @Reflect.Method("toString", returns = "String")
    fun toString(instance: Evaluator.Lambda): String {
        return "Lambda/${instance.ast.parameters.size}#${instance.hashCode()}"
    }

}
