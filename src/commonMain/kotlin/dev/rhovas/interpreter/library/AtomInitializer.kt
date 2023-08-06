package dev.rhovas.interpreter.library

import com.ionspin.kotlin.bignum.integer.BigInteger
import dev.rhovas.interpreter.environment.Component
import dev.rhovas.interpreter.environment.Object
import dev.rhovas.interpreter.environment.Type
import dev.rhovas.interpreter.parser.rhovas.RhovasAst

object AtomInitializer : Library.ComponentInitializer(Component.Class("Atom")) {

    override fun initialize() {
        inherits.add(Type.COMPARABLE[Type.ATOM])
        inherits.add(Type.HASHABLE[Type.ATOM])

        method("name",
            returns = Type.STRING
        ) { (instance) ->
            val instance = instance.value as RhovasAst.Atom
            Object(Type.STRING, instance.name)
        }

        method("compare", operator = "<=>",
            parameters = listOf("other" to Type.ATOM),
            returns = Type.INTEGER,
        ) { (instance, other) ->
            val instance = instance.value as RhovasAst.Atom
            val other = other.value as RhovasAst.Atom
            Object(Type.INTEGER, BigInteger.fromInt(instance.name.compareTo(other.name)))
        }

        method("to",
            parameters = listOf("type" to Type.TYPE[Type.STRING]),
            returns = Type.STRING,
        ) { (instance) ->
            val instance = instance.value as RhovasAst.Atom
            Object(Type.STRING, ":${instance.name}")
        }
    }

}
