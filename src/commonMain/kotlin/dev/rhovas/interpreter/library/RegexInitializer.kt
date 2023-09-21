package dev.rhovas.interpreter.library

import com.ionspin.kotlin.bignum.integer.BigInteger
import dev.rhovas.interpreter.environment.Component
import dev.rhovas.interpreter.environment.Object
import dev.rhovas.interpreter.environment.Type

object RegexInitializer : Library.ComponentInitializer(Component.Class("Regex")) {

    private val MATCH_TYPE get() = Type.STRUCT[listOf(
        "index" to Type.INTEGER,
        "value" to Type.INTEGER,
        "groups" to Type.LIST[Type.NULLABLE[Type.STRING]],
    )]

    override fun declare() {
        inherits.add(Type.ANY)
    }

    override fun define() {
        function("",
            parameters = listOf("regex" to Type.STRING),
            returns = Type.REGEX,
        ) { (pattern): T1<String> ->
            Object(Type.REGEX, Regex(pattern))
        }

        method("match",
            parameters = listOf("value" to Type.STRING),
            returns = Type.LIST[MATCH_TYPE]
        ) { (instance, value): T2<Regex, String> ->
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
