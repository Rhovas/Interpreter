package dev.rhovas.interpreter.library

import dev.rhovas.interpreter.environment.Function
import dev.rhovas.interpreter.environment.Object
import dev.rhovas.interpreter.evaluator.EvaluateException

object Reflect {

    annotation class Function(
        val value: String,
        val parameters: Array<String>,
        val returns: String = "Void",
    )

    fun initialize(initializer: Library.TypeInitializer) {
        initializer.javaClass.methods
            .filter { it.isAnnotationPresent(Function::class.java) }
            .forEach { method ->
                val annotation = method.getAnnotation(Function::class.java)
                initializer.type.methods.define(Function(annotation.value, annotation.parameters.size) { arguments ->
                    val transformed = arguments.withIndex().map {
                        if (annotation.parameters[it.index].isNotEmpty() && it.value.type.name != annotation.parameters[it.index]) {
                            throw EvaluateException("Argument ${it.index} to ${initializer.name}#${method.name}/${annotation.parameters.size}" +
                                    "must have type ${annotation.parameters[it.index]}, received ${arguments[it.index].type.name}")
                        }
                        if (method.parameters[it.index].type == Object::class.java) it.value else it.value.value
                    }
                    val result = method.invoke(initializer, *transformed.toTypedArray())
                    if (result is Object) result else Object(Library.TYPES[annotation.returns]!!, result)
                })
            }
    }

}
