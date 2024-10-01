package dev.rhovas.interpreter.library

import com.ionspin.kotlin.bignum.integer.BigInteger
import dev.rhovas.interpreter.environment.Component
import dev.rhovas.interpreter.environment.Object
import dev.rhovas.interpreter.environment.Type

object MapInitializer : Library.ComponentInitializer(Component.Class("Map")) {

    override fun declare() {
        generics.add(Type.Generic("K") { Type.HASHABLE[it] })
        generics.add(generic("V"))
        inherits.add(Type.EQUATABLE[Type.MAP[Type.Generic("K") { Type.HASHABLE[it] }, generic("V")]])
    }

    override fun define() {
        function("",
            generics = listOf(Type.Generic("K") { Type.HASHABLE[it] }, generic("V")),
            parameters = listOf("initial" to Type.MAP[Type.Generic("K") { Type.HASHABLE[it] }, generic("V")]),
            returns = Type.MAP[Type.Generic("K") { Type.HASHABLE[it] }, generic("V")],
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
            returns = Type.SET[Type.Generic("K") { Type.HASHABLE[it] }],
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
            returns = Type.LIST[Type.TUPLE[listOf(Type.Generic("K") { Type.HASHABLE[it] }, generic("V"))]],
        ) { (instance): T1<Map<Object.Hashable, Object>> ->
            val entryType = Type.TUPLE[listOf(generics["K"]!!, generics["V"]!!)]
            Object(Type.LIST[entryType], instance.entries.map { Object(entryType, listOf(it.key.instance, it.value)) })
        }

        method("get", operator = "[]",
            parameters = listOf("key" to Type.Generic("K") { Type.HASHABLE[it] }),
            returns = Type.NULLABLE[generic("V")],
        ) { (instance, key): T2<Map<Object.Hashable, Object>, Object> ->
            Object(Type.NULLABLE[generics["V"]!!], instance[Object.Hashable(key)]?.let { Pair(it, null) })
        }

        method("set", operator = "[]=",
            parameters = listOf("key" to Type.Generic("K") { Type.HASHABLE[it] }, "value" to generic("V")),
            returns = Type.VOID,
        ) { (instance, key, value): T3<MutableMap<Object.Hashable, Object>, Object, Object> ->
            instance[Object.Hashable(key)] = value
            Object(Type.VOID, Unit)
        }

        method("remove",
            parameters = listOf("key" to Type.Generic("K") { Type.HASHABLE[it] }),
            returns = Type.VOID,
        ) { (instance, key): T2<MutableMap<Object.Hashable, Object>, Object> ->
            instance.remove(Object.Hashable(key))
            Object(Type.VOID, Unit)
        }

        method("contains",
            parameters = listOf("key" to Type.Generic("K") { Type.HASHABLE[it] }),
            returns = Type.BOOLEAN,
        ) { (instance, key): T2<Map<Object.Hashable, Object>, Object> ->
            Object(Type.BOOLEAN, instance.containsKey(Object.Hashable(key)))
        }
    }

}
