package dev.rhovas.interpreter.library

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
            Reflect.initialize(it)
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

    }

}
