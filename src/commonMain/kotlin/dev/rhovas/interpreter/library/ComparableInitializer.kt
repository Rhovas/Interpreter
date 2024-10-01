package dev.rhovas.interpreter.library

import com.ionspin.kotlin.bignum.integer.BigInteger
import dev.rhovas.interpreter.environment.Component
import dev.rhovas.interpreter.environment.Modifiers
import dev.rhovas.interpreter.environment.Object
import dev.rhovas.interpreter.environment.Type

object ComparableInitializer : Library.ComponentInitializer(Component.Interface("Comparable", Modifiers(Modifiers.Inheritance.ABSTRACT))) {

    override fun declare() {
        generics.add(Type.Generic("T") { Type.COMPARABLE[it] })
        inherits.add(Type.EQUATABLE[Type.Generic("T") { Type.COMPARABLE[it] }])
    }

    override fun define() {
        method("compare", operator = "<=>",
            modifiers = Modifiers(Modifiers.Inheritance.VIRTUAL),
            parameters = listOf("other" to Type.COMPARABLE[Type.Generic("T") { Type.COMPARABLE[it] }]),
            returns = Type.INTEGER,
        ) { (instance, other): T2<Any?, Any?> ->
            require(instance is Comparable<*> && instance::class.isInstance(other))
            val result = (instance as Comparable<Any?>).compareTo(other)
            Object(Type.INTEGER, BigInteger.fromInt(result))
        }
    }

}
