package dev.rhovas.interpreter.library

import com.ionspin.kotlin.bignum.integer.BigInteger
import dev.rhovas.interpreter.environment.Component
import dev.rhovas.interpreter.environment.Object
import dev.rhovas.interpreter.environment.Type

object MapInitializer : Library.ComponentInitializer(Component.Class("Map")) {

    override fun initialize() {
        generics.add(generic("K", Type.HASHABLE[generic("K")]))
        generics.add(generic("V"))
        inherits.add(Type.EQUATABLE[Type.MAP.DYNAMIC])

        function("",
            generics = listOf(generic("K", Type.HASHABLE[generic("K")]), generic("V")),
            parameters = listOf("initial" to Type.MAP[generic("K", Type.HASHABLE[generic("K")]), generic("V")]),
            returns = Type.MAP[generic("K", Type.HASHABLE[generic("K")]), generic("V")],
        ) { (initial): T1<Map<Object.Hashable, Object>> ->
            Object(Type.MAP[generics["K"]!!, generics["V"]!!], initial.toMutableMap())
        }

        method("size",
            parameters = listOf(),
            returns = Type.INTEGER,
        ) { (instance): T1<Map<Object.Hashable, Object>> ->
            Object(Type.INTEGER, BigInteger.fromInt(instance.size))
        }

        method("empty",
            parameters = listOf(),
            returns = Type.BOOLEAN,
        ) { (instance): T1<Map<Object.Hashable, Object>> ->
            Object(Type.BOOLEAN, instance.isEmpty())
        }

        method("keys",
            parameters = listOf(),
            returns = Type.SET[generic("K", Type.HASHABLE[generic("K")])],
        ) { (instance): T1<Map<Object.Hashable, Object>> ->
            Object(Type.SET[generics["K"]!!], instance.keys)
        }

        method("values",
            parameters = listOf(),
            returns = Type.LIST[generic("V")],
        ) { (instance): T1<Map<Object.Hashable, Object>> ->
            Object(Type.LIST[generics["V"]!!], instance.values.toList())
        }

        method("entries",
            parameters = listOf(),
            returns = Type.LIST[Type.TUPLE[listOf(generic("K", Type.HASHABLE[generic("K")]), generic("V"))]],
        ) { (instance): T1<Map<Object.Hashable, Object>> ->
            val entryType = Type.TUPLE[listOf(generics["K"]!!, generics["V"]!!)]
            Object(Type.LIST[entryType], instance.entries.map { Object(entryType, listOf(it.key.instance, it.value)) })
        }

        method("get", operator = "[]",
            parameters = listOf("key" to generic("K", Type.HASHABLE[generic("K")])),
            returns = Type.NULLABLE[generic("V")],
        ) { (instance, key): T2<Map<Object.Hashable, Object>, Object> ->
            Object(Type.NULLABLE[generics["V"]!!], instance[Object.Hashable(key)]?.let { Pair(it, null) })
        }

        method("set", operator = "[]=",
            parameters = listOf("key" to generic("K", Type.HASHABLE[generic("K")]), "value" to generic("V")),
            returns = Type.VOID,
        ) { (instance, key, value): T3<MutableMap<Object.Hashable, Object>, Object, Object> ->
            instance[Object.Hashable(key)] = value
            Object(Type.VOID, Unit)
        }

        method("remove",
            parameters = listOf("key" to generic("K", Type.HASHABLE[generic("K")])),
            returns = Type.VOID,
        ) { (instance, key): T2<MutableMap<Object.Hashable, Object>, Object> ->
            instance.remove(Object.Hashable(key))
            Object(Type.VOID, Unit)
        }

        method("contains",
            parameters = listOf("key" to generic("K", Type.HASHABLE[generic("K")])),
            returns = Type.BOOLEAN,
        ) { (instance, key): T2<Map<Object.Hashable, Object>, Object> ->
            Object(Type.BOOLEAN, instance.containsKey(Object.Hashable(key)))
        }
    }

}
