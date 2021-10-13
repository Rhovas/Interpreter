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
            return Function(name, parameters.size) { arguments ->
                val transformed = arguments.withIndex().map {
                    if (parameters[it.index].isNotEmpty() && it.value.type.name != parameters[it.index]) {
                        //TODO: Error message format and index accuracy for methods
                        throw EvaluateException("Argument ${it.index} to ${initializer.type.name}#${name}/${parameters.size} " +
                                "must have type ${parameters[it.index]}, received ${arguments[it.index].type.name}")
                    }
                    if (method.parameters[it.index].type == Object::class.java) it.value else it.value.value
                }
                val result = method.invoke(initializer, *transformed.toTypedArray())
                if (result is Object) result else Object(Library.TYPES[returns]!!, result)
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
