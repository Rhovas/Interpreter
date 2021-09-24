package dev.rhovas.interpreter.library

import dev.rhovas.interpreter.environment.Function
import dev.rhovas.interpreter.environment.Object
import dev.rhovas.interpreter.parser.rhovas.RhovasAst

object ExceptionInitializer : Library.TypeInitializer("Exception") {

    override fun initialize() {
        type.methods.define(Function("message", 1) { arguments ->
            Object(Library.TYPES["String"]!!, arguments[0].value as String)
        })

        type.methods.define(Function("toString", 1) { arguments ->
            //TODO: Stacktrace
            Object(Library.TYPES["String"]!!, arguments[0].value as String)
        })
    }

}
