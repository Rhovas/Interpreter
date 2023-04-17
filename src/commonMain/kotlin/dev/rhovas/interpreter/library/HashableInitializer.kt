package dev.rhovas.interpreter.library

import com.ionspin.kotlin.bignum.integer.BigInteger
import dev.rhovas.interpreter.environment.Object
import dev.rhovas.interpreter.environment.Type

object HashableInitializer : Library.TypeInitializer("Hashable") {

    override fun initialize() {
        generics.add(generic("T", Type.HASHABLE.ANY))
        inherits.add(Type.EQUATABLE[generic("T")])

        function("hash",
            generics = listOf(generic("T", Type.HASHABLE.ANY)),
            parameters = listOf("instance" to generic("T")),
            returns = Type.INTEGER,
        ) { (instance) ->
            Object(Type.INTEGER, BigInteger.fromInt(instance.value.hashCode()))
        }
    }

}
