package dev.rhovas.interpreter.library

object NullInitializer : Library.TypeInitializer("Null") {

    @Reflect.Method("equals", operator = "==", parameters = ["Null"], returns = "Boolean")
    fun equals(instance: Void?, other: Void?): Boolean {
        return true
    }

    @Reflect.Method("toString", returns = "String")
    fun toString(instance: Void?): String {
        return "null"
    }

}
