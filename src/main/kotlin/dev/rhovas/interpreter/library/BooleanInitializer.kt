package dev.rhovas.interpreter.library

object BooleanInitializer : Library.TypeInitializer("Boolean") {

    @Reflect.Method("negate", operator = "!", returns = "Boolean")
    fun negate(instance: Boolean): Boolean {
        return !instance
    }

    @Reflect.Method("equals", operator = "==", parameters = ["Boolean"], returns = "Boolean")
    fun equals(instance: Boolean, other: Boolean): Boolean {
        return instance == other
    }

    @Reflect.Method("toString", returns = "String")
    fun toString(instance: Boolean): String {
        return instance.toString()
    }

}
