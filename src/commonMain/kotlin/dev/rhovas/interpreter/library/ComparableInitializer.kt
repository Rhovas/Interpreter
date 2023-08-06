package dev.rhovas.interpreter.library

import com.ionspin.kotlin.bignum.integer.BigInteger
import dev.rhovas.interpreter.environment.Component
import dev.rhovas.interpreter.environment.Modifiers
import dev.rhovas.interpreter.environment.Object
import dev.rhovas.interpreter.environment.Type

object ComparableInitializer : Library.ComponentInitializer(Component.Interface("Comparable", Modifiers(Modifiers.Inheritance.ABSTRACT))) {

    override fun initialize() {
        generics.add(generic("T", Type.COMPARABLE.DYNAMIC))
        inherits.add(Type.EQUATABLE[generic("T", Type.COMPARABLE.DYNAMIC)])

        function("compare", operator = "<=>",
            modifiers = Modifiers(Modifiers.Inheritance.VIRTUAL),
            generics = listOf(generic("T", Type.COMPARABLE.DYNAMIC)),
            parameters = listOf("instance" to generic("T"), "other" to generic("T")),
            returns = Type.INTEGER,
        ) { (instance, other) ->
            require(instance.value is Comparable<*> && instance.value::class.isInstance(other.value))
            val result = (instance.value as Comparable<Any?>).compareTo(other.value)
            Object(Type.INTEGER, BigInteger.fromInt(result))
        }
    }

}
