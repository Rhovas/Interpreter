package dev.rhovas.interpreter.library

import dev.rhovas.interpreter.EVALUATOR
import dev.rhovas.interpreter.environment.Component
import dev.rhovas.interpreter.environment.Function
import dev.rhovas.interpreter.environment.Modifiers
import dev.rhovas.interpreter.environment.Object
import dev.rhovas.interpreter.environment.Scope
import dev.rhovas.interpreter.environment.Type
import dev.rhovas.interpreter.environment.Variable
import dev.rhovas.interpreter.evaluator.EvaluateException
import kotlin.reflect.typeOf

object Library {

    val SCOPE = Scope.Definition(null)
    val TYPES get() = SCOPE.types

    init {
        // This list is intended to be in initialization order following DAG
        // requirements for inheritance (aka, no type should inherit from a type
        // defined later in the initialization order).
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
            TypeInitializer,
            ExceptionInitializer,
            ResultInitializer,
            NullableInitializer,
            LambdaInitializer,
            RegexInitializer,
            KernelInitializer,
            MathInitializer,
        )
        initializers.forEach { initializer ->
            TYPES.define(initializer.component.type)
        }
        initializers.forEach { initializer ->
            initializer.declare()
            initializer.generics.associateByTo(initializer.component.generics) { it.name }
        }
        initializers.forEach { initializer ->
            initializer.component.inherits.addAll(initializer.inherits)
            initializer.inherits.forEach { initializer.component.inherit(it) }
            initializer.define()
        }
        (KernelInitializer.component.scope as Scope.Definition).functions.collect().values.flatten().forEach {
            SCOPE.functions.define(it)
        }
    }

    fun type(name: String, generics: Map<String, Type> = mapOf()): Type.Reference {
        val type = TYPES[name]!! as Type.Reference
        return if (generics.isEmpty()) type else Type.Reference(type.component, generics)
    }

    abstract class ComponentInitializer(
        val component: Component<*>,
    ) {

        val generics = mutableListOf<Type.Generic>()
        val inherits = mutableListOf<Type.Reference>()

        abstract fun declare()

        abstract fun define()

        fun variable(
            name: String,
            type: Type,
            value: Any?
        ) {
            val variable = Variable.Definition(Variable.Declaration(name, type), Object(type, value))
            component.scope.variables.define(variable)
        }

        inline fun <reified T> function(
            name: String,
            operator: String? = null,
            modifiers: Modifiers = Modifiers(),
            generics: List<Type.Generic> = listOf(),
            parameters: List<Pair<String, Type>>,
            returns: Type,
            throws: List<Type> = listOf(),
            crossinline implementation: Context.(T) -> Object,
        ) {
            val declaration = Function.Declaration(name, modifiers, generics.associateByTo(linkedMapOf()) { it.name }, parameters.map { Variable.Declaration(it.first, it.second) }, returns, throws)
            val function = Function.Definition(declaration) { arguments ->
                val generics = mutableMapOf<String, Type>()
                EVALUATOR.require(declaration.isResolvedBy(arguments.map { it.type }, generics))
                val transform = arguments.indices.map {
                    when (typeOf<T>().arguments[it].type?.classifier) {
                        Object::class -> arguments[it]
                        else -> arguments[it].value
                    }
                }
                val wrapper = when (T::class) {
                    Unit::class -> Unit as T
                    T1::class -> T1(transform[0]) as T
                    T2::class -> T2(transform[0], transform[1]) as T
                    T3::class -> T3(transform[0], transform[1], transform[2]) as T
                    T4::class -> T4(transform[0], transform[1], transform[2], transform[3]) as T
                    T5::class -> T5(transform[0], transform[1], transform[2], transform[3], transform[4]) as T
                    else -> transform as T
                }
                val result = implementation.invoke(Context(generics), wrapper)
                EVALUATOR.require(result.type.isSubtypeOf(returns, generics))
                result
            }
            component.scope.functions.define(function)
            operator?.let { component.scope.functions.define(function, it) }
        }

        inline fun <reified T> method(
            name: String,
            operator: String? = null,
            modifiers: Modifiers = Modifiers(),
            generics: List<Type.Generic> = listOf(),
            parameters: List<Pair<String, Type>>,
            returns: Type,
            throws: List<Type> = listOf(),
            crossinline implementation: Context.(T) -> Object,
        ) {
            function(name, operator, modifiers, this.generics + generics, listOf("instance" to component.type) + parameters, returns, throws, implementation)
        }

        fun generic(name: String, bound: Type = Type.ANY) = Type.Generic(name, bound)

        data class Context(
            val generics: Map<String, Type>,
        ) {

            fun require(condition: Boolean, error: () -> EvaluateException) = EVALUATOR.require(condition, error)
            fun error(summary: String, details: String) = EVALUATOR.error(summary, details)

        }

        data class T1<A>(val a: A)
        data class T2<A, B>(val a: A, val b: B)
        data class T3<A, B, C>(val a: A, val b: B, val c: C)
        data class T4<A, B, C, D>(val a: A, val b: B, val c: C, val d: D)
        data class T5<A, B, C, D, E>(val a: A, val b: B, val c: C, val d: D, val e: E)

    }

}
