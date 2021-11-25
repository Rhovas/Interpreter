package dev.rhovas.interpreter.library

import dev.rhovas.interpreter.EVALUATOR
import dev.rhovas.interpreter.environment.Function
import dev.rhovas.interpreter.environment.Object
import dev.rhovas.interpreter.evaluator.EvaluateException
import dev.rhovas.interpreter.parser.Input
import java.lang.reflect.InvocationTargetException

object Reflect {

    annotation class Function(
        val value: String,
        val parameters: Array<String> = [],
        val returns: String = "Void",
    )

    annotation class Method(
        val value: String,
        val operator: String = "",
        val parameters: Array<String> = [],
        val returns: String = "Void",
    )

    fun initialize(initializer: Library.TypeInitializer) {
        fun function(
            method: java.lang.reflect.Method,
            name: String,
            parameters: Array<String>,
            returns: String
        ): dev.rhovas.interpreter.environment.Function {
            val parameters = parameters.map { Library.TYPES[it]!! }
            val returns = Library.TYPES[returns]!!
            return Function(name, parameters, returns) { arguments ->
                val transformed = parameters.indices.map {
                    when (parameters[it].name) {
                        "Any" -> arguments[it]
                        arguments[it].type.name -> arguments[it].value
                        else -> throw EVALUATOR.error(
                            null,
                            "Invalid argument type.",
                            "Function ${initializer.type.name}.${name} requires parameter ${it} to be type ${parameters[it].name}, received ${arguments[it].type.name}.",
                        )
                    }
                }
                try {
                    val result = method.invoke(initializer, *transformed.toTypedArray())
                    if (result is Object) result else Object(returns, result)
                } catch (e: InvocationTargetException) {
                    throw e.targetException
                }
            }
        }
        initializer.javaClass.methods
            .filter { it.isAnnotationPresent(Function::class.java) }
            .forEach { method ->
                val annotation = method.getAnnotation(Function::class.java)
                val function = function(method, annotation.value, annotation.parameters, annotation.returns)
                initializer.type.methods.define(function)
            }
        initializer.javaClass.methods
            .filter { it.isAnnotationPresent(Method::class.java) }
            .forEach { method ->
                val annotation = method.getAnnotation(Method::class.java)
                val function = function(method, annotation.value, arrayOf(initializer.type.name) + annotation.parameters, annotation.returns)
                initializer.type.methods.define(function)
                if (annotation.operator.isNotEmpty()) {
                    initializer.type.methods.define(function.copy(name = annotation.operator))
                }
            }
    }

}
