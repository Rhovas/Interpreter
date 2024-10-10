package dev.rhovas.interpreter.library

import com.ionspin.kotlin.bignum.integer.BigInteger
import dev.rhovas.interpreter.environment.Component
import dev.rhovas.interpreter.environment.Modifiers
import dev.rhovas.interpreter.environment.Object
import dev.rhovas.interpreter.environment.type.Type

object HashableInitializer : Library.ComponentInitializer(Component.Interface("Hashable")) {

    override fun declare() {
        generics.add(Type.Generic("T") { Type.HASHABLE[it] })
        inherits.add(Type.EQUATABLE[Type.Generic("T") { Type.HASHABLE[it] }])
    }

    override fun define() {
        method("hash",
            modifiers = Modifiers(Modifiers.Inheritance.VIRTUAL),
            parameters = listOf(),
            returns = Type.INTEGER,
        ) { (instance): T1<Any?> ->
            Object(Type.INTEGER, BigInteger.fromInt(instance.hashCode()))
        }
    }

}
