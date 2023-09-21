package dev.rhovas.interpreter.library

import dev.rhovas.interpreter.environment.Component
import dev.rhovas.interpreter.environment.Modifiers
import dev.rhovas.interpreter.environment.Object
import dev.rhovas.interpreter.environment.Type

object EquatableInitializer : Library.ComponentInitializer(Component.Interface("Equatable")) {

    override fun declare() {
        generics.add(generic("T", Type.EQUATABLE.DYNAMIC))
        inherits.add(Type.ANY)
    }

    override fun define() {
        function("equals", operator = "==",
            modifiers = Modifiers(Modifiers.Inheritance.VIRTUAL),
            generics = listOf(generic("T", Type.EQUATABLE.DYNAMIC)),
            parameters = listOf("instance" to generic("T"), "other" to generic("T")),
            returns = Type.BOOLEAN,
        ) { (instance, other): T2<Any?, Any?> ->
            fun equals(value: Any?, other: Any?): Boolean {
                return when {
                    value is Object && other is Object -> value.methods.equals(other)
                    value is Map<*, *> && other is Map<*, *> -> value.keys == other.keys && value.all { equals(it.value, other[it.key]!!) }
                    value is Collection<*> && other is Collection<*> -> value.size == other.size && value.zip(other).all { equals(it.first, it.second) }
                    else -> value == other
                }
            }
            Object(Type.BOOLEAN, equals(instance, other))
        }
    }

}
