package dev.rhovas.interpreter.library

@Reflect.Type("Exception")
object ExceptionInitializer : Library.TypeInitializer("Exception") {

    override fun initialize() {
        inherits.add(Library.TYPES["Any"]!!)
    }

    @Reflect.Method("message", returns = Reflect.Type("String"))
    fun message(instance: String): String {
        return instance
    }

    @Reflect.Method("toString", returns = Reflect.Type("String"))
    fun toString(instance: String): String {
        return instance //TODO: Stacktrace
    }

}
