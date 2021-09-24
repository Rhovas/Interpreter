package dev.rhovas.interpreter.library

import dev.rhovas.interpreter.environment.Scope
import dev.rhovas.interpreter.environment.Type

object Library {

    val TYPES = mutableMapOf<String, Type>()

    fun initialize() {
        val initializers = listOf(
            VoidInitializer,
            NullInitializer,
            BooleanInitializer,
            IntegerInitializer,
            DecimalInitializer,
            StringInitializer,
            AtomInitializer,
            ListInitializer,
            ObjectInitializer,
            ExceptionInitializer,
        )
        initializers.forEach { TYPES[it.name] = it.type }
        initializers.forEach { it.initialize() }
    }

    abstract class TypeInitializer(
        val name: String,
    ) {

        val type = Type(name, Scope(null))

        abstract fun initialize()

    }

}
