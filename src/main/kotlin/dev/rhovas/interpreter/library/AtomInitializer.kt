package dev.rhovas.interpreter.library

import dev.rhovas.interpreter.parser.rhovas.RhovasAst
import java.math.BigInteger

object AtomInitializer : Library.TypeInitializer("Atom") {

    @Reflect.Method("equals", operator = "==", parameters = ["Atom"], returns = "Boolean")
    fun equals(instance: RhovasAst.Atom, other: RhovasAst.Atom): Boolean {
        return instance == other
    }

    @Reflect.Method("compare", operator = "<=>", parameters = ["Atom"], returns = "Integer")
    fun compare(instance: RhovasAst.Atom, other: RhovasAst.Atom): BigInteger {
        return BigInteger.valueOf(instance.name.compareTo(other.name).toLong())
    }

    @Reflect.Method("toString", returns = "String")
    fun toString(instance: RhovasAst.Atom): String {
        return ":${instance.name}"
    }

}
