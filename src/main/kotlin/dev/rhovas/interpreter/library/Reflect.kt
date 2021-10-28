package dev.rhovas.interpreter.library

import dev.rhovas.interpreter.environment.Function
import dev.rhovas.interpreter.environment.Object
import dev.rhovas.interpreter.evaluator.EvaluateException

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
                        else -> throw EvaluateException("Invalid argument to function ${name}/${parameters.size}: expected ${parameters[it].name}, received ${arguments[it].type.name}.")
                    }
                }
                val result = method.invoke(initializer, *transformed.toTypedArray())
                if (result is Object) result else Object(returns, result)
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
