package dev.rhovas.interpreter.library

import com.ionspin.kotlin.bignum.integer.BigInteger
import dev.rhovas.interpreter.environment.Component
import dev.rhovas.interpreter.environment.Object
import dev.rhovas.interpreter.environment.type.Type
import dev.rhovas.interpreter.parser.rhovas.RhovasAst

object AtomInitializer : Library.ComponentInitializer(Component.Class("Atom")) {

    override fun declare() {
        inherits.add(Type.COMPARABLE[Type.ATOM])
        inherits.add(Type.HASHABLE[Type.ATOM])
    }

    override fun define() {
        method("name",
            parameters = listOf(),
            returns = Type.STRING,
        ) { (instance): T1<RhovasAst.Atom> ->
            Object(Type.STRING, instance.name)
        }

        method("compare", operator = "<=>",
            parameters = listOf("other" to Type.ATOM),
            returns = Type.INTEGER,
        ) { (instance, other): T2<RhovasAst.Atom, RhovasAst.Atom> ->
            Object(Type.INTEGER, BigInteger.fromInt(instance.name.compareTo(other.name)))
        }

        method("to",
            parameters = listOf("type" to Type.TYPE[Type.STRING]),
            returns = Type.STRING,
        ) { (instance, _type): T2<RhovasAst.Atom, Type> ->
            Object(Type.STRING, ":${instance.name}")
        }
    }

}
