package dev.rhovas.interpreter.library

@Reflect.Type("Null")
object NullInitializer : Library.TypeInitializer("Null") {

    override fun initialize() {
        inherits.add(Library.TYPES["Any"]!!)
    }

    @Reflect.Method("equals", operator = "==",
        parameters = [Reflect.Type("Null")],
        returns = Reflect.Type("Boolean"),
    )
    fun equals(instance: Void?, other: Void?): Boolean {
        return true
    }

    @Reflect.Method("toString", returns = Reflect.Type("String"))
    fun toString(instance: Void?): String {
        return "null"
    }

}
