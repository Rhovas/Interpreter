package dev.rhovas.interpreter.library

import dev.rhovas.interpreter.EVALUATOR
import dev.rhovas.interpreter.environment.Function
import dev.rhovas.interpreter.environment.Object
import dev.rhovas.interpreter.environment.Variable
import java.lang.reflect.InvocationTargetException

object Reflect {

    annotation class Property(
        val value: String,
        val type: String,
    )

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
        ): dev.rhovas.interpreter.environment.Function.Definition {
            val parameters = parameters.map { Pair(it, Library.TYPES[it]!!) }
            val returns = Library.TYPES[returns]!!
            val function = dev.rhovas.interpreter.environment.Function.Definition(name, parameters, returns)
            function.implementation = { arguments ->
                val transformed = parameters.indices.map {
                    if (parameters[it].second == Library.TYPES["Any"]!!) {
                        arguments[it]
                    } else {
                        EVALUATOR.require(arguments[it].type.isSubtypeOf(parameters[it].second))
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
            return function
        }
        initializer.javaClass.methods
            .filter { it.isAnnotationPresent(Property::class.java) }
            .forEach { method ->
                val annotation = method.getAnnotation(Property::class.java)
                val function = function(method, annotation.value, arrayOf(initializer.type.name), annotation.type)
                initializer.type.scope.functions.define(function)
            }
        initializer.javaClass.methods
            .filter { it.isAnnotationPresent(Function::class.java) }
            .forEach { method ->
                val annotation = method.getAnnotation(Function::class.java)
                val function = function(method, annotation.value, annotation.parameters, annotation.returns)
                initializer.type.scope.functions.define(function)
            }
        initializer.javaClass.methods
            .filter { it.isAnnotationPresent(Method::class.java) }
            .forEach { method ->
                val annotation = method.getAnnotation(Method::class.java)
                val function = function(method, annotation.value, arrayOf(initializer.type.name) + annotation.parameters, annotation.returns)
                initializer.type.scope.functions.define(function)
                if (annotation.operator.isNotEmpty()) {
                    val overload = function.copy(name = annotation.operator)
                    overload.implementation = function.implementation
                    initializer.type.scope.functions.define(overload)
                }
            }
    }

}
