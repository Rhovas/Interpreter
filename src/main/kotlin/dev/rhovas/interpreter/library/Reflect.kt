package dev.rhovas.interpreter.library

import dev.rhovas.interpreter.environment.Object
import java.lang.reflect.InvocationTargetException

object Reflect {

    annotation class Property(
        val value: String,
        val type: Type,
    )

    annotation class Function(
        val value: String,
        val parameters: Array<Type> = [],
        val returns: Type = Type("Void"),
    )

    annotation class Method(
        val value: String,
        val operator: String = "",
        val parameters: Array<Type> = [],
        val returns: Type = Type("Void"),
    )

    annotation class Type(
        val value: String,
        val generics: Array<Type> = [],
    )

    fun initialize(initializer: Library.TypeInitializer) {
        val type = initializer.javaClass.getAnnotation(Type::class.java)
        //TODO: Generic bounds
        type.generics.forEach { initializer.generics.add(dev.rhovas.interpreter.environment.Type.Generic(it.value, Library.TYPES["Any"]!!)) }
        val generics = initializer.generics.associateBy { it.name }
        fun function(
            method: java.lang.reflect.Method,
            name: String,
            parameters: Array<Type>,
            returns: Type
        ): dev.rhovas.interpreter.environment.Function.Definition {
            val function = dev.rhovas.interpreter.environment.Function.Definition(dev.rhovas.interpreter.environment.Function.Declaration(name,
                generics.values.toList(),
                parameters.withIndex().map { Pair("val_${it.index}", resolve(it.value, generics)) },
                resolve(returns, generics),
                listOf(), //TODO: Throws
            ))
            function.implementation = { arguments ->
                val transformed = parameters.indices.map {
                    if (method.parameters[it].type == Object::class.java) {
                        arguments[it]
                    } else {
                        //TODO: Fails for generics
                        //EVALUATOR.require(arguments[it].type.isSubtypeOf(function.parameters[it].second))
                        arguments[it].value
                    }
                }
                try {
                    val result = method.invoke(initializer, *transformed.toTypedArray())
                    if (result is Object) result else Object(function.returns.bind(generics.mapValues { Library.TYPES["Dynamic"]!! }), result)
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
                val function = function(method, annotation.value, arrayOf(type), annotation.type)
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
                val function = function(method, annotation.value, arrayOf(type) + annotation.parameters, annotation.returns)
                initializer.type.scope.functions.define(function)
                if (annotation.operator.isNotEmpty()) {
                    val overload = dev.rhovas.interpreter.environment.Function.Definition(function.declaration.copy(name = annotation.operator))
                    overload.implementation = function.implementation
                    initializer.type.scope.functions.define(overload)
                }
            }
    }

    private fun resolve(type: Type, generics: Map<String, dev.rhovas.interpreter.environment.Type.Generic>): dev.rhovas.interpreter.environment.Type {
        return generics[type.value] ?: dev.rhovas.interpreter.environment.Type.Reference(
            Library.TYPES[type.value]!!.base,
            type.generics.map { resolve(it, generics) }
        )
    }

}
