package dev.rhovas.interpreter.library

import dev.rhovas.interpreter.environment.Object

@Reflect.Type("Any")
object AnyInitializer: Library.TypeInitializer("Any") {

    override fun initialize() {}

    @Reflect.Method("toString",
        parameters = [Reflect.Type("Any")],
        returns = Reflect.Type("String"),
    )
    fun toString(instance: Object): String {
        return instance.value.toString()
    }

}
