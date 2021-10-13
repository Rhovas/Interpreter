package dev.rhovas.interpreter.library

object ExceptionInitializer : Library.TypeInitializer("Exception") {

    @Reflect.Method("message", returns = "String")
    fun message(instance: String): String {
        return instance
    }

    @Reflect.Method("toString", returns = "String")
    fun toString(instance: String): String {
        return instance //TODO: Stacktrace
    }

}
