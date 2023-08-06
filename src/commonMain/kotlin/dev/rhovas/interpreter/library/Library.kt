package dev.rhovas.interpreter.library

import dev.rhovas.interpreter.EVALUATOR
import dev.rhovas.interpreter.environment.Component
import dev.rhovas.interpreter.environment.Function
import dev.rhovas.interpreter.environment.Modifiers
import dev.rhovas.interpreter.environment.Object
import dev.rhovas.interpreter.environment.Scope
import dev.rhovas.interpreter.environment.Type
import dev.rhovas.interpreter.environment.Variable

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
        initializers.forEach { initializer ->
            TYPES.define(initializer.component.type)
        }
        initializers.forEach { initializer ->
            initializer.initialize()
            initializer.component.generics.addAll(initializer.generics)
            initializer.inherits.forEach { initializer.component.inherit(it) }
        }
        (KernelInitializer.component.scope as Scope.Definition).functions.collect().values.flatten().forEach {
            SCOPE.functions.define(it)
        }
    }

    fun type(name: String, vararg generics: Type = arrayOf()): Type.Reference {
        val type = TYPES[name]!! as Type.Reference
        return if (generics.isEmpty()) type else Type.Reference(type.component, generics.toList())
    }

    abstract class ComponentInitializer(
        val component: Component<*>,
    ) {

        val generics = mutableListOf<Type.Generic>()
        val inherits = mutableListOf<Type.Reference>()

        abstract fun initialize()

        fun variable(
            name: String,
            type: Type,
            value: Any?
        ) {
            val variable = Variable.Definition(Variable.Declaration(name, type, false), Object(type, value))
            (component.scope as Scope<in Variable.Definition, *>).variables.define(variable)
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
                        "The native function ${component.name}.${name} requires argument ${it} to be type ${parameters[it].second}, but received ${arguments[it]}.",
                    ) }
                }
                implementation.invoke(arguments)
            }
            (component.scope as Scope<*, in Function.Definition>).functions.define(function)
            operator?.let { component.scope.functions.define(function, it) }
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
            function(name, operator, modifiers, this.generics + generics, listOf("instance" to component.type) + parameters, returns, throws, implementation)
        }

        fun generic(name: String, bound: Type = Type.ANY) = Type.Generic(name, bound)

    }

}
