package dev.rhovas.interpreter.library

import dev.rhovas.interpreter.environment.Scope
import dev.rhovas.interpreter.environment.Type

object Library {

    val TYPES = mutableMapOf<String, Type>()
    val SCOPE: Scope = Scope(null)

    fun initialize() {
        TYPES["Any"] = Type("Any", Scope(null))
        TYPES["Type"] = Type("Type", Scope(null))
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

        val type = Type(name, scope)

        open fun initialize() {}

    }

}
