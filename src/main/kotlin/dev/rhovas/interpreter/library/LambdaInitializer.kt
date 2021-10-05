package dev.rhovas.interpreter.library

import dev.rhovas.interpreter.environment.Function
import dev.rhovas.interpreter.environment.Object
import dev.rhovas.interpreter.evaluator.EvaluateException
import dev.rhovas.interpreter.evaluator.Evaluator

object LambdaInitializer : Library.TypeInitializer("Lambda") {

    override fun initialize() {
        type.methods.define(Function("invoke", 0) { arguments ->
            val lambda = arguments[0].value as Evaluator.Lambda
            if (lambda.ast.parameters.size != arguments.size - 1) {
                throw EvaluateException("Lambda requires ${lambda.ast.parameters} parameter(s), received ${arguments.size}.")
            }
            (arguments[0].value as Evaluator.Lambda).invoke(
                lambda.ast.parameters.zip(arguments.subList(1, arguments.size)).associate { it.first to it.second }
            )
        })
        for (arity in 1..10) {
            type.methods.define(type.methods["invoke", 0]!!.copy(arity = arity))
        }
        type.methods.define(Function("toString", 1) { arguments ->
            val lambda = (arguments[0].value as Evaluator.Lambda)
            Object(Library.TYPES["String"]!!, "Lambda/${lambda.ast.parameters.size}#${lambda.hashCode()}")
        })
    }

}
