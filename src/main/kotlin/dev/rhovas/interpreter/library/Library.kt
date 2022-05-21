package dev.rhovas.interpreter.library

import dev.rhovas.interpreter.environment.Scope
import dev.rhovas.interpreter.environment.Type

object Library {

    val TYPES = mutableMapOf<String, Type.Reference>()
    val SCOPE: Scope = Scope(null)

    init {
        TYPES["Any"] = Type.Base("Any", listOf(), listOf(), Scope(null)).reference
        TYPES["Dynamic"] = Type.Base("Dynamic", listOf(), listOf(), Scope(null)).reference
        TYPES["Type"] = Type.Base("Type", listOf(), listOf(), Scope(null)).reference
        val initializers = listOf(
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
        )
        initializers.forEach { TYPES[it.name] = it.type.reference }
        initializers.forEach {
            Reflect.initialize(it)
            it.initialize()
        }
        KernelInitializer.scope.functions.collect().values.flatten().forEach { SCOPE.functions.define(it) }
    }

    abstract class TypeInitializer(val name: String) {

        val generics = mutableListOf<Type.Generic>()
        val inherits = mutableListOf<Type>()
        val scope = Scope(null)
        val type = Type.Base(name, generics, inherits, scope)

        abstract fun initialize()

    }

}
