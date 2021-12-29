package dev.rhovas.interpreter.library

@Reflect.Type("Boolean")
object BooleanInitializer : Library.TypeInitializer("Boolean") {

    override fun initialize() {
        inherits.add(Library.TYPES["Any"]!!)
    }

    @Reflect.Method("negate", operator = "!",
        returns = Reflect.Type("Boolean")
    )
    fun negate(instance: Boolean): Boolean {
        return !instance
    }

    @Reflect.Method("equals", operator = "==",
        parameters = [Reflect.Type("Boolean")],
        returns = Reflect.Type("Boolean"),
    )
    fun equals(instance: Boolean, other: Boolean): Boolean {
        return instance == other
    }

    @Reflect.Method("toString",
        returns = Reflect.Type("String")
    )
    fun toString(instance: Boolean): String {
        return instance.toString()
    }

}
