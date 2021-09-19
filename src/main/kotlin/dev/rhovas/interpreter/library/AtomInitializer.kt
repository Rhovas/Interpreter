package dev.rhovas.interpreter.library

import dev.rhovas.interpreter.environment.Function
import dev.rhovas.interpreter.environment.Object
import dev.rhovas.interpreter.parser.rhovas.RhovasAst

object AtomInitializer : Library.TypeInitializer("Atom") {

    override fun initialize() {
        type.methods.define(Function("toString", 1) { arguments ->
            Object(Library.TYPES["String"]!!, ":" + (arguments[0].value as RhovasAst.Atom).name)
        })
    }

}
