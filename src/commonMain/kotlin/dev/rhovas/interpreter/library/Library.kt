package dev.rhovas.interpreter.library

import dev.rhovas.interpreter.EVALUATOR
import dev.rhovas.interpreter.environment.*
import dev.rhovas.interpreter.environment.Function

object Library {

    val SCOPE = Scope.Definition(null)
    val TYPES get() = SCOPE.types

    init {
        val initializers = listOf(
            DynamicInitializer,
            AnyInitializer,
            EquatableInitializer,
            ComparableInitializer,
            HashableInitializer,
            VoidInitializer,
            BooleanInitializer,
            IntegerInitializer,
            DecimalInitializer,
            StringInitializer,
            AtomInitializer,
            TupleInitializer,
            IterableInitializer,
            IteratorInitializer,
            ListInitializer,
            SetInitializer,
            MapInitializer,
            StructInitializer,
            LambdaInitializer,
            TypeInitializer,
            ExceptionInitializer,
            ResultInitializer,
            NullableInitializer,
            RegexInitializer,
            KernelInitializer,
            MathInitializer,
        )
        initializers.forEach { type ->
            TYPES.define(type.base.reference)
        }
        initializers.forEach { type ->
            type.initialize()
            type.base.generics.addAll(type.generics)
            type.inherits.forEach { type.base.inherit(it) }
        }
        KernelInitializer.base.scope.functions.collect().values.flatten().forEach {
            SCOPE.functions.define(it)
        }
    }

    fun type(name: String, vararg generics: Type = arrayOf()): Type.Reference {
        val type = TYPES[name]!! as Type.Reference
        return if (generics.isEmpty()) type else Type.Reference(type.base, generics.toList())
    }

    abstract class TypeInitializer(
        name: String,
        modifiers: Modifiers = Modifiers(Modifiers.Inheritance.DEFAULT),
    ) {

        val generics = mutableListOf<Type.Generic>()
        val inherits = mutableListOf<Type.Reference>()
        val base = Type.Base(name, modifiers, Scope.Definition(null))

        abstract fun initialize()

        fun variable(
            name: String,
            type: Type,
            value: Any?
        ) {
            val variable = Variable.Definition(Variable.Declaration(name, type, false), Object(type, value))
            base.scope.variables.define(variable)
        }

        fun function(
            name: String,
            operator: String? = null,
            modifiers: Modifiers = Modifiers(Modifiers.Inheritance.DEFAULT),
            generics: List<Type.Generic> = listOf(),
            parameters: List<Pair<String, Type>> = listOf(),
            returns: Type = Type.VOID,
            throws: List<Type> = listOf(),
            implementation: (List<Object>) -> Object,
        ) {
            val function = Function.Definition(Function.Declaration(modifiers, name, generics, parameters.map { Variable.Declaration(it.first, it.second, false) }, returns, throws)) { arguments ->
                arguments.indices.forEach {
                    EVALUATOR.require(arguments[it].type.isSubtypeOf(parameters[it].second)) { EVALUATOR.error(null,
                        "Invalid argument.",
                        "The native function ${base.name}.${name} requires argument ${it} to be type ${parameters[it].second}, but received ${arguments[it]}.",
                    ) }
                }
                implementation.invoke(arguments)
            }
            base.scope.functions.define(function)
            operator?.let { base.scope.functions.define(function, it) }
        }

        fun method(
            name: String,
            operator: String? = null,
            modifiers: Modifiers = Modifiers(Modifiers.Inheritance.DEFAULT),
            generics: List<Type.Generic> = listOf(),
            parameters: List<Pair<String, Type>> = listOf(),
            returns: Type = Type.VOID,
            throws: List<Type> = listOf(),
            implementation: (List<Object>) -> Object,
        ) {
            function(name, operator, modifiers, this.generics + generics, listOf("instance" to base.reference) + parameters, returns, throws, implementation)
        }

        fun generic(name: String, bound: Type = Type.ANY) = Type.Generic(name, bound)

    }

}
