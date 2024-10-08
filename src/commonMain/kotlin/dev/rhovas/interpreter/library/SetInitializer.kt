package dev.rhovas.interpreter.library

import com.ionspin.kotlin.bignum.integer.BigInteger
import dev.rhovas.interpreter.environment.Component
import dev.rhovas.interpreter.environment.Object
import dev.rhovas.interpreter.environment.type.Type

object SetInitializer : Library.ComponentInitializer(Component.Class("Set")) {

    override fun declare() {
        generics.add(generic("T"))
        inherits.add(Type.ITERABLE[generic("T")])
        inherits.add(Type.EQUATABLE[Type.SET.DYNAMIC])
    }

    override fun define() {
        function("",
            generics = listOf(generic("T")),
            parameters = listOf("initial" to Type.LIST[generic("T")]),
            returns = Type.SET[generic("T")],
        ) { (initial): T1<List<Object>> ->
            Object(Type.SET[generics["T"]!!], initial.map { Object.Hashable(it) }.toMutableSet())
        }

        method("size",
            parameters = listOf(),
            returns = Type.INTEGER,
        ) { (instance): T1<Set<Object.Hashable>> ->
            Object(Type.INTEGER, BigInteger.fromInt(instance.size))
        }

        method("empty",
            parameters = listOf(),
            returns = Type.BOOLEAN,
        ) { (instance): T1<Set<Object.Hashable>> ->
            Object(Type.BOOLEAN, instance.isEmpty())
        }

        method("contains", operator = "[]",
            parameters = listOf("element" to generic("T")),
            returns = Type.BOOLEAN,
        ) { (instance, element): T2<Set<Object.Hashable>, Object> ->
            Object(Type.BOOLEAN, instance.contains(Object.Hashable(element)))
        }

        method("add",
            parameters = listOf("element" to generic("T")),
            returns = Type.VOID,
        ) { (instance, element): T2<MutableSet<Object.Hashable>, Object> ->
            instance.add(Object.Hashable(element))
            Object(Type.VOID, Unit)
        }

        method("remove",
            parameters = listOf("element" to generic("T")),
            returns = Type.VOID,
        ) { (instance, element): T2<MutableSet<Object.Hashable>, Object> ->
            instance.remove(Object.Hashable(element))
            Object(Type.VOID, Unit)
        }

        method("union",
            parameters = listOf("other" to Type.SET[generic("T")]),
            returns = Type.SET[generic("T")],
        ) { (instance, other): T2<Set<Object.Hashable>, Set<Object.Hashable>> ->
            Object(Type.SET[generics["T"]!!], instance.union(other).toMutableSet())
        }

        method("intersection",
            parameters = listOf("other" to Type.SET[generic("T")]),
            returns = Type.SET[generic("T")],
        ) { (instance, other): T2<Set<Object.Hashable>, Set<Object.Hashable>> ->
            Object(Type.SET[generics["T"]!!], instance.intersect(other).toMutableSet())
        }

        method("difference",
            parameters = listOf("other" to Type.SET[generic("T")]),
            returns = Type.SET[generic("T")],
        ) { (instance, other): T2<Set<Object.Hashable>, Set<Object.Hashable>> ->
            Object(Type.SET[generics["T"]!!], instance.minus(other).toMutableSet())
        }
    }

}
