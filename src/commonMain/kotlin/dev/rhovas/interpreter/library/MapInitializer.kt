package dev.rhovas.interpreter.library

import com.ionspin.kotlin.bignum.integer.BigInteger
import dev.rhovas.interpreter.environment.Object
import dev.rhovas.interpreter.environment.Type
import dev.rhovas.interpreter.environment.Variable

object MapInitializer : Library.TypeInitializer("Map") {

    override fun initialize() {
        generics.add(generic("K", Type.HASHABLE[generic("K")]))
        generics.add(generic("V"))
        inherits.add(Type.EQUATABLE[Type.MAP[Type.DYNAMIC, Type.DYNAMIC]])

        function("",
            generics = listOf(generic("K", Type.HASHABLE[generic("K")]), generic("V")),
            parameters = listOf("initial" to Type.MAP[generic("K", Type.HASHABLE[generic("K")]), generic("V")]),
            returns = Type.MAP[generic("K", Type.HASHABLE[generic("K")]), generic("V")],
        ) { (initial) ->
            Object(initial.type, (initial.value as Map<Object.Hashable, Object>).toMutableMap())
        }

        method("size",
            returns = Type.INTEGER,
        ) { (instance) ->
            val instance = instance.value as Map<Object.Hashable, Object>
            Object(Type.INTEGER, BigInteger.fromInt(instance.size))
        }

        method("empty",
            returns = Type.BOOLEAN,
        ) { (instance) ->
            val instance = instance.value as Map<Object.Hashable, Object>
            Object(Type.BOOLEAN, instance.isEmpty())
        }

        method("keys",
            returns = Type.SET[generic("K", Type.HASHABLE[generic("K")])],
        ) { (instance) ->
            val keyType = instance.type.generic("K", Type.SET.GENERIC)!!
            val instance = instance.value as Map<Object.Hashable, Object>
            Object(Type.SET[keyType], instance.keys)
        }

        method("values",
            returns = Type.LIST[generic("V")],
        ) { (instance) ->
            val valueType = instance.type.generic("V", Type.MAP.GENERIC)!!
            val instance = instance.value as Map<Object.Hashable, Object>
            Object(Type.LIST[valueType], instance.values.toList())
        }

        method("entries",
            returns = Type.LIST[Type.TUPLE[Variable.Declaration("key", generic("K", Type.HASHABLE[generic("K")]), false), Variable.Declaration("value", generic("V"), false)]],
        ) { (instance) ->
            val keyType = instance.type.generic("K", Type.MAP.GENERIC)!!
            val valueType = instance.type.generic("V", Type.MAP.GENERIC)!!
            val entryType = Type.TUPLE[Variable.Declaration("0", keyType, false), Variable.Declaration("1", valueType, false)]
            val instance = instance.value as Map<Object.Hashable, Object>
            Object(Type.LIST[entryType], instance.entries.map { Object(entryType, listOf(it.key.instance, it.value)) })
        }

        method("get", operator = "[]",
            parameters = listOf("key" to generic("K", Type.HASHABLE[generic("K")])),
            returns = Type.NULLABLE[generic("V")],
        ) { (instance, key) ->
            val valueType = instance.type.generic("V", Type.MAP.GENERIC)!!
            val instance = instance.value as Map<Object.Hashable, Object>
            Object(Type.NULLABLE[valueType], instance[Object.Hashable(key)]?.let { Pair(it, null) })
        }

        method("set", operator = "[]=",
            parameters = listOf("key" to generic("K", Type.HASHABLE[generic("K")]), "value" to generic("V")),
            returns = Type.VOID,
        ) { (instance, key, value) ->
            val instance = instance.value as MutableMap<Object.Hashable, Object>
            instance[Object.Hashable(key)] = value
            Object(Type.VOID, Unit)
        }

        method("remove",
            parameters = listOf("key" to generic("K", Type.HASHABLE[generic("K")])),
            returns = Type.VOID,
        ) { (instance, key, value) ->
            val instance = instance.value as MutableMap<Object.Hashable, Object>
            instance.remove(Object.Hashable(key))
            Object(Type.VOID, Unit)
        }

        method("contains",
            parameters = listOf("key" to generic("K", Type.HASHABLE[generic("K")])),
            returns = Type.BOOLEAN,
        ) { (instance, key) ->
            val instance = instance.value as MutableMap<Object.Hashable, Object>
            Object(Type.BOOLEAN, instance.containsKey(Object.Hashable(key)))
        }
    }

}
