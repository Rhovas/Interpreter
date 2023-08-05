package dev.rhovas.interpreter.library

import com.ionspin.kotlin.bignum.integer.BigInteger
import dev.rhovas.interpreter.environment.Object
import dev.rhovas.interpreter.environment.Type
import dev.rhovas.interpreter.environment.Variable

object RegexInitializer : Library.TypeInitializer("Regex", Type.Component.CLASS) {

    private val MATCH_TYPE get() = Type.STRUCT[Type.Struct(mapOf(
        "index" to Variable.Declaration("index", Type.INTEGER, false),
        "value" to Variable.Declaration("value", Type.INTEGER, false),
        "groups" to Variable.Declaration("groups", Type.LIST[Type.NULLABLE[Type.STRING]], false),
    ))]

    override fun initialize() {
        inherits.add(Type.ANY)

        function("",
            parameters = listOf("regex" to Type.STRING),
            returns = Type.REGEX,
        ) { (pattern) ->
            val pattern = pattern.value as String
            Object(Type.REGEX, Regex(pattern))
        }

        method("match",
            parameters = listOf("value" to Type.STRING),
            returns = Type.LIST[MATCH_TYPE]
        ) { (instance, value) ->
            val instance = instance.value as Regex
            val value = value.value as String
            Object(Type.LIST[MATCH_TYPE], instance.findAll(value).toList().map {
                Object(MATCH_TYPE, mapOf(
                    "index" to Object(Type.INTEGER, BigInteger.fromInt(it.range.first)),
                    "value" to Object(Type.STRING, it.value),
                    "groups" to Object(Type.LIST[Type.NULLABLE[Type.STRING]], it.groups.drop(1).map {
                        Object(Type.NULLABLE[Type.STRING], it?.let { Pair(Object(Type.STRING, it.value), null) })
                    })
                ))
            })
        }
    }

}
