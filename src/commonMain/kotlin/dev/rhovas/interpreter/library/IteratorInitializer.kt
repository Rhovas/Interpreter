package dev.rhovas.interpreter.library

import dev.rhovas.interpreter.environment.Object
import dev.rhovas.interpreter.environment.Type

object IteratorInitializer : Library.TypeInitializer("Iterator") {

    override fun initialize() {
        generics.add(generic("T"))
        inherits.add(Type.ANY)

        method("next",
            returns = Type.NULLABLE[generic("T")],
        ) { (instance) ->
            val instance = instance.value as Iterator<Object>
            val result = if (instance.hasNext()) instance.next() else null
            Object(Type.NULLABLE[result?.type ?: Type.DYNAMIC], result?.let { Pair(it, null) })
        }
    }

}
