package dev.rhovas.interpreter.library

import dev.rhovas.interpreter.environment.Component
import dev.rhovas.interpreter.environment.Object
import dev.rhovas.interpreter.environment.Type

object NullableInitializer : Library.ComponentInitializer(Component.Class("Nullable")) {

    override fun declare() {
        generics.add(generic("T"))
        inherits.add(Type.RESULT[generic("T"), Type.EXCEPTION])
    }

    override fun define() {
        function("",
            generics = listOf(generic("T")),
            parameters = listOf("value" to generic("T")),
            returns = Type.NULLABLE[generic("T")],
        ) { (value): T1<Object> ->
            Object(Type.NULLABLE[generics["T"]!!], Pair(value, null))
        }
    }

}
