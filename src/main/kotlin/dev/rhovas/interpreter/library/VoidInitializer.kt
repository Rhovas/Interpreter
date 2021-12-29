package dev.rhovas.interpreter.library

@Reflect.Type("Void")
object VoidInitializer : Library.TypeInitializer("Void") {

    override fun initialize() {
        inherits.add(Library.TYPES["Any"]!!)
    }

    @Reflect.Method("equals", operator = "==",
        parameters = [Reflect.Type("Void")],
        returns = Reflect.Type("Boolean")
    )
    fun equals(instance: Unit, other: Unit): Boolean {
        return true
    }

    @Reflect.Method("toString", returns = Reflect.Type("String"))
    fun toString(instance: Unit): String {
        return "void"
    }

}
