package dev.rhovas.interpreter.library

object VoidInitializer : Library.TypeInitializer("Void") {

    @Reflect.Method("equals", operator = "==", parameters = ["Void"], returns = "Boolean")
    fun equals(instance: Unit, other: Unit): Boolean {
        return true
    }

    @Reflect.Method("toString", returns = "String")
    fun toString(instance: Unit): String {
        return "null"
    }

}
