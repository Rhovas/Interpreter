package dev.rhovas.interpreter.library

import dev.rhovas.interpreter.EVALUATOR
import dev.rhovas.interpreter.environment.Function
import dev.rhovas.interpreter.environment.Object
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
                    if (parameters[it] == Library.TYPES["Any"]!!) {
                        arguments[it]
                    } else {
                        EVALUATOR.require(arguments[it].type.isSubtypeOf(parameters[it]))
                        arguments[it].value
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
