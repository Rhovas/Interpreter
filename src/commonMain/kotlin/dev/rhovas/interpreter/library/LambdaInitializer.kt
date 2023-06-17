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
            if (instance.ast.parameters.isNotEmpty()) {
                EVALUATOR.require(arguments.size == instance.ast.parameters.size) { EVALUATOR.error(instance.ast,
                    "Invalid lambda argument count.",
                    "Lambda requires arguments of size ${instance.ast.parameters.size}, but received ${arguments.size}.",
                ) }
                instance.invoke(arguments.indices.map { Triple(it.toString(), arguments[it].type, arguments[it]) }, returnsType)
            } else if (arguments.isEmpty()) {
                instance.invoke(listOf(Triple("val", Type.VOID, Object(Type.VOID, Unit))), returnsType)
            } else if (arguments.size == 1) {
                instance.invoke(listOf(Triple("val", arguments[0].type, arguments[0])), returnsType)
            } else {
                val type = Type.TUPLE[Type.Tuple(arguments.withIndex().map { Variable.Declaration(it.index.toString(), it.value.type, false) })]
                instance.invoke(listOf(Triple("val", type, Object(type, arguments))), returnsType)
            }
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
