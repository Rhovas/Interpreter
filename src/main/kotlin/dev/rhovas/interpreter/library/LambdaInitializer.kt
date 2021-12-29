package dev.rhovas.interpreter.library

import dev.rhovas.interpreter.EVALUATOR
import dev.rhovas.interpreter.environment.Object
import dev.rhovas.interpreter.environment.Type
import dev.rhovas.interpreter.evaluator.EvaluateException
import dev.rhovas.interpreter.evaluator.Evaluator

@Reflect.Type("Lambda", [Reflect.Type("T"), Reflect.Type("R")])
object LambdaInitializer : Library.TypeInitializer("Lambda") {

    override fun initialize() {
        inherits.add(Library.TYPES["Any"]!!)
    }

    @Reflect.Method("invoke",
        parameters = [Reflect.Type("T")],
        returns = Reflect.Type("R")
    )
    fun invoke(instance: Evaluator.Lambda, arguments: List<Object>): Object {
        EVALUATOR.require(arguments.size == instance.ast.parameters.size) { EVALUATOR.error(
            instance.ast,
            "Invalid lambda argument count.",
            "Lambda requires arguments of size ${instance.ast.parameters.size}, but received ${arguments.size}.",
        ) }
        return instance.invoke(arguments.indices.map {
            val parameter = instance.ast.parameters.getOrNull(it)
            Triple(parameter?.first ?: "val_${it}", parameter?.second?.let { instance.evaluator.visit(it).value as Type } ?: Library.TYPES["Any"]!!, arguments[it])
        }, Library.TYPES["Any"]!!)
    }

    @Reflect.Method("toString",
        returns = Reflect.Type("String")
    )
    fun toString(instance: Evaluator.Lambda): String {
        return "Lambda/${instance.ast.parameters.size}#${instance.hashCode()}"
    }

}
