package dev.rhovas.interpreter.library

import dev.rhovas.interpreter.EVALUATOR
import dev.rhovas.interpreter.environment.Function
import dev.rhovas.interpreter.environment.Object
import dev.rhovas.interpreter.environment.Scope
import dev.rhovas.interpreter.environment.Type

object Library {

    val SCOPE = Scope.Definition(null)
    val TYPES get() = SCOPE.types

    init {
        TYPES.define(Type.Base("Type", listOf(), listOf(), Scope.Definition(null)).reference)
        TYPES.define(Type.Base("Dynamic", listOf(), listOf(), Scope.Definition(null)).reference)
        val initializers = listOf(
            AnyInitializer,
            KernelInitializer,
            VoidInitializer,
            NullInitializer,
            BooleanInitializer,
            IntegerInitializer,
            DecimalInitializer,
            StringInitializer,
            AtomInitializer,
            ListInitializer,
            ObjectInitializer,
            LambdaInitializer,
            ExceptionInitializer,
            NullableInitializer,
        )
        initializers.forEach {
            TYPES.define(it.type.reference)
        }
        initializers.forEach {
            it.initialize()
        }
        KernelInitializer.scope.functions.collect().values.flatten().forEach {
            SCOPE.functions.define(it)
        }
    }

    abstract class TypeInitializer(val name: String) {

        val generics = mutableListOf<Type.Generic>()
        val inherits = mutableListOf<Type>()
        val scope = Scope.Definition(null)
        val type = Type.Base(name, generics, inherits, scope)

        abstract fun initialize()

        fun function(
            name: String,
            operator: String? = null,
            generics: List<Type.Generic> = listOf(),
            parameters: List<Pair<String, Type>> = listOf(),
            returns: Type = type("Void"),
            throws: List<Type> = listOf(),
            implementation: (List<Object>) -> Object,
        ) {
            val function = Function.Definition(Function.Declaration(name, generics, parameters, returns, throws)).also {
                it.implementation = { arguments ->
                    //TODO: Address subtyping issues with variant generics
                    /*arguments.indices.forEach {
                        EVALUATOR.require(arguments[it].type.isSubtypeOf(parameters[it].second)) { EVALUATOR.error(
                            null,
                            "Invalid argument.",
                            "The native function ${type.name}.${name} requires argument ${it} to be type ${parameters[it].second}, but received ${arguments[it]}.",
                        ) }
                    }*/
                    implementation.invoke(arguments)
                }
            }
            scope.functions.define(function)
            operator?.let { scope.functions.define(function, it) }
        }

        fun method(
            name: String,
            operator: String? = null,
            generics: List<Type.Generic> = listOf(),
            parameters: List<Pair<String, Type>> = listOf(),
            returns: Type = type("Void"),
            throws: List<Type> = listOf(),
            implementation: (List<Object>) -> Object,
        ) {
            function(name, operator, this.generics + generics, listOf("instance" to type.reference) + parameters, returns, throws, implementation)
        }

        fun type(name: String): Type {
            return TYPES[name]!!
        }

        fun type(name: String, vararg generics: String): Type {
            return Type.Reference(TYPES[name]!!.base, generics.map { TYPES[it]!! })
        }

        fun type(name: String, vararg generics: Type): Type {
            return Type.Reference(TYPES[name]!!.base, generics.toList())
        }

        fun generic(name: String, bound: Type = type("Any")): Type.Generic {
            return Type.Generic(name, bound)
        }

    }

}
