package dev.rhovas.interpreter.library

import com.ionspin.kotlin.bignum.integer.BigInteger
import dev.rhovas.interpreter.environment.Modifiers
import dev.rhovas.interpreter.environment.Object
import dev.rhovas.interpreter.environment.Type

object HashableInitializer : Library.TypeInitializer("Hashable", Type.Component.INTERFACE, Modifiers(Modifiers.Inheritance.ABSTRACT)) {

    override fun initialize() {
        generics.add(generic("T", Type.HASHABLE.DYNAMIC))
        inherits.add(Type.EQUATABLE[generic("T", Type.HASHABLE.DYNAMIC)])

        function("hash",
            modifiers = Modifiers(Modifiers.Inheritance.VIRTUAL),
            generics = listOf(generic("T", Type.HASHABLE.DYNAMIC)),
            parameters = listOf("instance" to generic("T")),
            returns = Type.INTEGER,
        ) { (instance) ->
            Object(Type.INTEGER, BigInteger.fromInt(instance.value.hashCode()))
        }
    }

}
