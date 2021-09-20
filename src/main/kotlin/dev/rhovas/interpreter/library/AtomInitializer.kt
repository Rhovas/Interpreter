package dev.rhovas.interpreter.library

import dev.rhovas.interpreter.environment.Function
import dev.rhovas.interpreter.environment.Object
import dev.rhovas.interpreter.parser.rhovas.RhovasAst
import java.math.BigInteger

object AtomInitializer : Library.TypeInitializer("Atom") {

    override fun initialize() {
        type.methods.define(Function("equals", 2) { arguments ->
            Object(Library.TYPES["Boolean"]!!, arguments[0].value == arguments[1].value)
        })
        type.methods.define(type.methods["equals", 2]!!.copy(name = "=="))

        type.methods.define(Function("compare", 2) { arguments ->
            val self = arguments[0].value as RhovasAst.Atom
            val other = arguments[1].value as RhovasAst.Atom
            Object(Library.TYPES["Integer"]!!, BigInteger.valueOf(self.name.compareTo(other.name).toLong()))
        })
        type.methods.define(type.methods["compare", 2]!!.copy(name = "<=>"))

        type.methods.define(Function("toString", 1) { arguments ->
            Object(Library.TYPES["String"]!!, ":" + (arguments[0].value as RhovasAst.Atom).name)
        })
    }

}
