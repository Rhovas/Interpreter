package dev.rhovas.interpreter.library

import dev.rhovas.interpreter.environment.Function
import dev.rhovas.interpreter.environment.Object
import dev.rhovas.interpreter.parser.rhovas.RhovasAst

object ObjectInitializer : Library.TypeInitializer("Object") {

    override fun initialize() {
        type.methods.define(Function("toString", 1) { arguments ->
            Object(Library.TYPES["String"]!!, (arguments[0].value as Map<String, Object>).mapValues {
                it.value.methods["toString", 0]!!.invoke(listOf()).value.toString()
            })
        })
    }

}
