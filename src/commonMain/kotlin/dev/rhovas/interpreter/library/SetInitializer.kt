package dev.rhovas.interpreter.library

import com.ionspin.kotlin.bignum.integer.BigInteger
import dev.rhovas.interpreter.EVALUATOR
import dev.rhovas.interpreter.environment.Object
import dev.rhovas.interpreter.environment.Type
import dev.rhovas.interpreter.environment.Variable
import dev.rhovas.interpreter.evaluator.Evaluator

object SetInitializer : Library.TypeInitializer("Set") {

    override fun initialize() {
        generics.add(generic("T"))
        inherits.add(Type.ITERABLE[generic("T")])
        inherits.add(Type.EQUATABLE[Type.SET[Type.DYNAMIC]])

        function("",
            generics = listOf(generic("T")),
            parameters = listOf("initial" to Type.LIST[generic("T")]),
            returns = Type.SET[generic("T")],
        ) { (initial) ->
            val elementType = initial.type.generic("T", Type.LIST.GENERIC)!!
            val initial = initial.value as List<Object>
            Object(Type.SET[elementType], initial.map { Object.Hashable(it) }.toMutableSet())
        }

        method("size",
            returns = Type.INTEGER,
        ) { (instance) ->
            val instance = instance.value as Set<Object.Hashable>
            Object(Type.INTEGER, BigInteger.fromInt(instance.size))
        }

        method("empty",
            returns = Type.BOOLEAN,
        ) { (instance) ->
            val instance = instance.value as Set<Object.Hashable>
            Object(Type.BOOLEAN, instance.isEmpty())
        }

        method("contains", operator = "[]",
            parameters = listOf("element" to generic("T")),
            returns = Type.BOOLEAN,
        ) { (instance, element) ->
            val instance = instance.value as Set<Object.Hashable>
            Object(Type.BOOLEAN, instance.contains(Object.Hashable(element)))
        }

        method("add",
            parameters = listOf("element" to generic("T")),
        ) { (instance, element) ->
            val instance = instance.value as MutableSet<Object.Hashable>
            instance.add(Object.Hashable(element))
            Object(Type.VOID, Unit)
        }

        method("remove",
            parameters = listOf("element" to generic("T")),
        ) { (instance, element) ->
            val instance = instance.value as MutableSet<Object.Hashable>
            instance.remove(Object.Hashable(element))
            Object(Type.VOID, Unit)
        }

        method("union",
            parameters = listOf("other" to Type.SET[generic("T")]),
            returns = Type.SET[generic("T")],
        ) { (instance, other) ->
            val type = instance.type
            val instance = instance.value as Set<Object.Hashable>
            val other = other.value as Set<Object.Hashable>
            Object(type, instance.union(other).toMutableSet())
        }

        method("intersection",
            parameters = listOf("other" to Type.SET[generic("T")]),
            returns = Type.SET[generic("T")],
        ) { (instance, other) ->
            val type = instance.type
            val instance = instance.value as Set<Object.Hashable>
            val other = other.value as Set<Object.Hashable>
            Object(type, instance.intersect(other).toMutableSet())
        }

        method("difference",
            parameters = listOf("other" to Type.SET[generic("T")]),
            returns = Type.SET[generic("T")],
        ) { (instance, other) ->
            val type = instance.type
            val instance = instance.value as Set<Object.Hashable>
            val other = other.value as Set<Object.Hashable>
            Object(type, instance.minus(other).toMutableSet())
        }
    }

}
