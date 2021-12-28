package dev.rhovas.interpreter.library

import dev.rhovas.interpreter.environment.Scope
import dev.rhovas.interpreter.environment.Type

object Library {

    val TYPES = mutableMapOf<String, Type.Base>()
    val SCOPE: Scope = Scope(null)

    fun initialize() {
        TYPES["Any"] = Type.Base("Any", listOf(), listOf(), Scope(null))
        TYPES["Dynamic"] = Type.Base("Dynamic", listOf(), listOf(), Scope(null))
        TYPES["Type"] = Type.Base("Type", listOf(), listOf(), Scope(null))
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
        initializers.forEach { TYPES[it.name] = it.type }
        initializers.forEach {
            Reflect.initialize(it)
            it.initialize()
        }
    }

    abstract class TypeInitializer(
        val name: String,
        scope: Scope = Scope(null),
    ) {

        val type = Type.Base(name, listOf(), listOf(), scope)

        open fun initialize() {}

    }

}
