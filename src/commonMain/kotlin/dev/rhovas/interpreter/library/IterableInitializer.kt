package dev.rhovas.interpreter.library

import dev.rhovas.interpreter.EVALUATOR
import dev.rhovas.interpreter.environment.Object
import dev.rhovas.interpreter.environment.Type
import dev.rhovas.interpreter.environment.Variable
import dev.rhovas.interpreter.evaluator.Evaluator

object IterableInitializer : Library.TypeInitializer("Iterable") {

    override fun initialize() {
        generics.add(generic("T"))
        inherits.add(Type.ANY)

        method("iterator",
            returns = Type.ITERATOR[generic("T")]
        ) { (instance) ->
            val elementType = instance.type.generic("T", Type.ITERABLE.GENERIC)!!
            val instance = instance.value as Iterable<Object>
            instance.asIterable()
            Object(Type.ITERATOR[elementType], instance.iterator())
        }

        method("for",
            parameters = listOf("lambda" to Type.LAMBDA[Type.TUPLE[listOf(generic("T"))], Type.VOID, Type.DYNAMIC]),
        ) { (instance, lambda) ->
            val instance = instance.value as List<Object>
            val lambda = lambda.value as Evaluator.Lambda
            instance.forEach { lambda.invoke(listOf(it), Type.VOID) }
            Object(Type.VOID, Unit)
        }
    }

}
