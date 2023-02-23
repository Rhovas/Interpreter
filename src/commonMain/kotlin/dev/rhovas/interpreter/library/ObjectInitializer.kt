package dev.rhovas.interpreter.library

import dev.rhovas.interpreter.EVALUATOR
import dev.rhovas.interpreter.environment.Object
import dev.rhovas.interpreter.environment.Type
import dev.rhovas.interpreter.parser.rhovas.RhovasAst

object ObjectInitializer : Library.TypeInitializer("Object") {

    override fun initialize() {
        inherits.add(Type.ANY)

        method("get", operator = "[]",
            parameters = listOf("key" to Type.ATOM),
            returns = Type.DYNAMIC,
        ) { (instance, key) ->
            val instance = instance.value as Map<String, Object>
            val key = key.value as RhovasAst.Atom
            instance[key.name] ?: Object(Type.NULLABLE.ANY, null)
        }

        method("set", operator = "[]=",
            parameters = listOf("key" to Type.ATOM, "value" to Type.DYNAMIC),
            returns = Type.DYNAMIC,
        ) { (instance, key, value) ->
            val instance = instance.value as MutableMap<String, Object>
            val key = key.value as RhovasAst.Atom
            instance[key.name] = value
            Object(Type.VOID, null)
        }

        method("equals", operator = "==",
            parameters = listOf("other" to Type.OBJECT),
            returns = Type.BOOLEAN,
        ) { (instance, other) ->
            val instance = instance.value as Map<String, Object>
            val other = other.value as Map<String, Object>
            Object(Type.BOOLEAN, instance.keys == other.keys && instance.keys.all {
                val method = instance[it]!!.methods["==", listOf(instance[it]!!.type)] ?: throw EVALUATOR.error(
                    null,
                    "Undefined method.",
                    "The method ${instance[it]!!.type.base.name}.==(${instance[it]!!.type}) is undefined.",
                )
                if (other[it]!!.type.isSubtypeOf(method.parameters[0].type)) method.invoke(listOf(other[it]!!)).value as Boolean else false
            })
        }

        method("to",
            parameters = listOf("type" to Type.TYPE[Type.STRING]),
            returns = Type.STRING,
        ) { (instance) ->
            val instance = instance.value as Map<String, Object>
            Object(Type.STRING, instance.mapValues { it.value.methods.toString() }.toString())
        }
    }

}
