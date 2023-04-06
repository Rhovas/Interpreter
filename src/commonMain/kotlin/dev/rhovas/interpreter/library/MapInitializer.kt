package dev.rhovas.interpreter.library

import com.ionspin.kotlin.bignum.integer.BigInteger
import dev.rhovas.interpreter.environment.Object
import dev.rhovas.interpreter.environment.Type

object MapInitializer : Library.TypeInitializer("Map") {

    data class Key(val key: Object) {

        override fun equals(other: Any?): Boolean {
            return other is Key && key.methods.equals(other.key)
        }

        override fun hashCode(): Int {
            return key.value.hashCode()
        }

        override fun toString(): String {
            return key.methods.toString()
        }

    }

    override fun initialize() {
        generics.add(generic("K", Type.EQUATABLE[generic("K")]))
        generics.add(generic("V"))
        inherits.add(Type.EQUATABLE[Type.MAP.ANY])

        function("",
            generics = listOf(generic("K"), generic("V")),
            parameters = listOf("initial" to Type.MAP[generic("K"), generic("V")]),
            returns = Type.MAP[generic("K"), generic("V")],
        ) { (initial) ->
            val type = initial.type
            Object(initial.type, (initial.value as Map<Key, Object>).toMutableMap())
        }

        method("size",
            returns = Type.INTEGER,
        ) { (instance) ->
            val instance = instance.value as Map<Key, Object>
            Object(Type.INTEGER, BigInteger.fromInt(instance.size))
        }

        method("empty",
            returns = Type.BOOLEAN,
        ) { (instance) ->
            val instance = instance.value as Map<Key, Object>
            Object(Type.BOOLEAN, instance.isEmpty())
        }

        method("keys",
            returns = Type.LIST[generic("K")],
        ) { (instance) ->
            val keyType = (instance.type as Type.Reference).generics[0]
            val instance = instance.value as Map<Key, Object>
            Object(Type.LIST[keyType], instance.keys.map { it.key })
        }

        method("values",
            returns = Type.LIST[generic("V")],
        ) { (instance) ->
            val valueType = (instance.type as Type.Reference).generics[1]
            val instance = instance.value as Map<Key, Object>
            Object(Type.LIST[valueType], instance.values.toList())
        }

        method("entries",
            returns = Type.LIST[Type.TUPLE[generic("K"), generic("V")]],
        ) { (instance) ->
            val (keyType, valueType) = (instance.type as Type.Reference).generics
            val instance = instance.value as Map<Key, Object>
            Object(Type.LIST[Type.TUPLE[keyType, valueType]], instance.entries.map { Object(Type.TUPLE[it.key.key.type, it.value.type], listOf(it.key.key, it.value)) })
        }

        method("get", operator = "[]",
            parameters = listOf("key" to generic("K")),
            returns = Type.NULLABLE[generic("V")],
        ) { (instance, key) ->
            val valueType = (instance.type as Type.Reference).generics[1]
            val instance = instance.value as Map<Key, Object>
            Object(Type.NULLABLE[valueType], instance[Key(key)]?.let { Pair(it, null) })
        }

        method("set", operator = "[]=",
            parameters = listOf("key" to generic("K"), "value" to generic("V")),
            returns = Type.VOID,
        ) { (instance, key, value) ->
            val instance = instance.value as MutableMap<Any?, Object>
            instance[Key(key)] = value
            Object(Type.VOID, null)
        }

        method("contains",
            parameters = listOf("key" to generic("K")),
            returns = Type.BOOLEAN,
        ) { (instance, key) ->
            val instance = instance.value as MutableMap<Any?, Object>
            Object(Type.BOOLEAN, instance.containsKey(Key(key)))
        }

        method("to",
            parameters = listOf("type" to Type.TYPE[Type.STRING]),
            returns = Type.STRING,
        ) { (instance) ->
            val instance = instance.value as Map<Key, Object>
            Object(Type.STRING, instance.mapValues { it.value.methods.toString() }.toString())
        }
    }

}
