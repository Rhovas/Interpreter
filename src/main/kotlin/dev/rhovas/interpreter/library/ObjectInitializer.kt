package dev.rhovas.interpreter.library

import dev.rhovas.interpreter.EVALUATOR
import dev.rhovas.interpreter.environment.Object
import dev.rhovas.interpreter.environment.Type
import dev.rhovas.interpreter.parser.rhovas.RhovasAst
import java.math.BigInteger

object ObjectInitializer : Library.TypeInitializer("Object") {

    override fun initialize() {
        inherits.add(type("Any"))

        method("get", operator = "[]",
            parameters = listOf("key" to type("Atom")),
            returns = type("Dynamic"),
        ) { (instance, key) ->
            val instance = instance.value as Map<String, Object>
            val key = key.value as RhovasAst.Atom
            instance[key.name] ?: Object(Library.TYPES["Null"]!!, null)
        }

        method("set", operator = "[]=",
            parameters = listOf("key" to type("Atom"), "value" to type("Dynamic")),
            returns = type("Dynamic"),
        ) { (instance, key, value) ->
            val instance = instance.value as MutableMap<String, Object>
            val key = key.value as RhovasAst.Atom
            instance[key.name] = value
            Object(type("Void"), null)
        }

        method("equals", operator = "==",
            parameters = listOf("other" to type("Object")),
            returns = type("Boolean"),
        ) { (instance, other) ->
            val instance = instance.value as Map<String, Object>
            val other = other.value as Map<String, Object>
            Object(type("Boolean"), instance.keys == other.keys && instance.keys.all {
                val method = instance[it]!!.methods["==", listOf(instance[it]!!.type)] ?: throw EVALUATOR.error(
                    null,
                    "Undefined method.",
                    "The method ${instance[it]!!.type.base.name}.==(${instance[it]!!.type}) is undefined.",
                )
                if (other[it]!!.type.isSubtypeOf(method.parameters[0].type)) method.invoke(listOf(other[it]!!)).value as Boolean else false
            })
        }

        method("toString",
            returns = type("String"),
        ) { (instance) ->
            val instance = instance.value as Map<String, Object>
            Object(type("String"), instance.mapValues {
                it.value.methods["toString", listOf()]!!.invoke(listOf()).value as String
            }.toString())
        }
    }

}
