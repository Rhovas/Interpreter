package dev.rhovas.interpreter.library

import dev.rhovas.interpreter.environment.Component
import dev.rhovas.interpreter.environment.Modifiers
import dev.rhovas.interpreter.environment.Object
import dev.rhovas.interpreter.environment.Type

object IteratorInitializer : Library.ComponentInitializer(Component.Class("Iterator", Modifiers(Modifiers.Inheritance.VIRTUAL))) {

    override fun declare() {
        generics.add(generic("T"))
        inherits.add(Type.ANY)
    }

    override fun define() {
        method("next",
            modifiers = Modifiers(Modifiers.Inheritance.VIRTUAL),
            parameters = listOf(),
            returns = Type.NULLABLE[generic("T")],
        ) { (instance): T1<Iterator<Object>> ->
            val result = if (instance.hasNext()) instance.next() else null
            Object(Type.NULLABLE[generics["T"]!!], result?.let { Pair(it, null) })
        }
    }

}
