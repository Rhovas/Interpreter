package dev.rhovas.interpreter.library

import dev.rhovas.interpreter.parser.rhovas.RhovasAst
import java.math.BigInteger

@Reflect.Type("Atom")
object AtomInitializer : Library.TypeInitializer("Atom") {

    override fun initialize() {
        inherits.add(Library.TYPES["Any"]!!)
    }

    @Reflect.Property("name", type = Reflect.Type("String"))
    fun name(instance: RhovasAst.Atom): String {
        return instance.name
    }

    @Reflect.Method("equals", operator = "==",
        parameters = [Reflect.Type("Atom")],
        returns = Reflect.Type("Boolean")
    )
    fun equals(instance: RhovasAst.Atom, other: RhovasAst.Atom): Boolean {
        return instance == other
    }

    @Reflect.Method("compare", operator = "<=>",
        parameters = [Reflect.Type("Atom")],
        returns = Reflect.Type("Integer")
    )
    fun compare(instance: RhovasAst.Atom, other: RhovasAst.Atom): BigInteger {
        return BigInteger.valueOf(instance.name.compareTo(other.name).toLong())
    }

    @Reflect.Method("toString", returns = Reflect.Type("String"))
    fun toString(instance: RhovasAst.Atom): String {
        return ":${instance.name}"
    }

}
