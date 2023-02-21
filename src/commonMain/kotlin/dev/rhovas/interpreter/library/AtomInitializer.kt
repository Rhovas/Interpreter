package dev.rhovas.interpreter.library

import com.ionspin.kotlin.bignum.integer.BigInteger
import dev.rhovas.interpreter.environment.Object
import dev.rhovas.interpreter.environment.Type
import dev.rhovas.interpreter.parser.rhovas.RhovasAst

object AtomInitializer : Library.TypeInitializer("Atom") {

    override fun initialize() {
        inherits.add(Type.ANY)

        method("name",
            returns = Type.STRING
        ) { (instance) ->
            val instance = instance.value as RhovasAst.Atom
            Object(Type.STRING, instance.name)
        }

        method("equals", operator = "==",
            parameters = listOf("other" to Type.ATOM),
            returns = Type.BOOLEAN,
        ) { (instance, other) ->
            Object(Type.BOOLEAN, instance.value == other.value)
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
            Object(Type.STRING, ":${instance.value}")
        }
    }

}
