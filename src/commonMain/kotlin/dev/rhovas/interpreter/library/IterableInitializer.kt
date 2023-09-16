package dev.rhovas.interpreter.library

import dev.rhovas.interpreter.environment.Component
import dev.rhovas.interpreter.environment.Modifiers
import dev.rhovas.interpreter.environment.Object
import dev.rhovas.interpreter.environment.Type
import dev.rhovas.interpreter.evaluator.Evaluator

object IterableInitializer : Library.ComponentInitializer(Component.Interface("Iterable")) {

    override fun initialize() {
        generics.add(generic("T"))
        inherits.add(Type.ANY)

        method("iterator",
            modifiers = Modifiers(Modifiers.Inheritance.VIRTUAL),
            parameters = listOf(),
            returns = Type.ITERATOR[generic("T")],
        ) { (instance): T1<Iterable<Object>> ->
            val elementType = arguments[0].type.generic("T", Type.ITERABLE.GENERIC)!!
            Object(Type.ITERATOR[elementType], instance.iterator())
        }

        method("for",
            parameters = listOf("lambda" to Type.LAMBDA[Type.TUPLE[listOf(generic("T"))], Type.VOID, Type.DYNAMIC]),
            returns = Type.VOID,
        ) { (instance, lambda): T2<Iterable<Object>, Evaluator.Lambda> ->
            instance.forEach { lambda.invoke(listOf(it), Type.VOID) }
            Object(Type.VOID, Unit)
        }
    }

}
