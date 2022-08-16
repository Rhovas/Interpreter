package dev.rhovas.interpreter.library

import dev.rhovas.interpreter.environment.Object
import dev.rhovas.interpreter.parser.rhovas.RhovasAst
import java.math.BigInteger

object AtomInitializer : Library.TypeInitializer("Atom") {

    override fun initialize() {
        inherits.add(type("Any"))

        method("name",
            returns = type("String")
        ) { (instance) ->
            val instance = instance.value as RhovasAst.Atom
            Object(type("String"), instance.name)
        }

        method("equals", operator = "==",
            parameters = listOf("other" to type("Atom")),
            returns = type("Boolean"),
        ) { (instance, other) ->
            Object(type("Boolean"), instance.value == other.value)
        }

        method("compare", operator = "<=>",
            parameters = listOf("other" to type("Atom")),
            returns = type("Integer"),
        ) { (instance, other) ->
            val instance = instance.value as RhovasAst.Atom
            val other = other.value as RhovasAst.Atom
            Object(type("Integer"), BigInteger.valueOf(instance.name.compareTo(other.name).toLong()))
        }

        method("toString",
            returns = type("String"),
        ) { (instance) ->
            Object(type("String"), ":${instance.value}")
        }
    }

}
