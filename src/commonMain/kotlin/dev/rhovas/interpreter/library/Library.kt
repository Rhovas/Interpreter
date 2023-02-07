package dev.rhovas.interpreter.library

import dev.rhovas.interpreter.environment.*
import dev.rhovas.interpreter.environment.Function

object Library {

    val SCOPE = Scope.Definition(null)
    val TYPES get() = SCOPE.types

    init {
        TYPES.define(Type.Base("Type", listOf(Type.Generic("T", AnyInitializer.type.reference)), listOf(), Scope.Definition(null)).reference)
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
        initializers.forEach { type ->
            type.initialize()
            //Hacky approach to add methods from inherited types (only disjoint overloads)
            type.inherits.forEach { supertype ->
                supertype.base.scope.functions.collect()
                    .flatMap { it.value }
                    .filter { function -> (
                        (function.parameters.firstOrNull()?.type?.isSubtypeOf(supertype) ?: false) &&
                        type.scope.functions[function.name, function.parameters.size].all { it.isDisjointWith(function) }
                    ) }
                    .forEach { type.scope.functions.define(it) }
            }
        }
        KernelInitializer.scope.functions.collect().values.flatten().forEach {
            SCOPE.functions.define(it)
        }
    }

    fun type(name: String, vararg generics: Type = arrayOf()): Type {
        return Type.Reference(TYPES[name]!!.base, generics.toList())
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
            returns: Type = Type.VOID,
            throws: List<Type> = listOf(),
            implementation: (List<Object>) -> Object,
        ) {
            val function = Function.Definition(Function.Declaration(name, generics, parameters.map { Variable.Declaration(it.first, it.second, false) }, returns, throws)).also {
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
            returns: Type = Type.VOID,
            throws: List<Type> = listOf(),
            implementation: (List<Object>) -> Object,
        ) {
            function(name, operator, this.generics + generics, listOf("instance" to type.reference) + parameters, returns, throws, implementation)
        }

        fun generic(name: String, bound: Type = Type.ANY) = Type.Generic(name, bound)

    }

}
