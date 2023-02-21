package dev.rhovas.interpreter.library

import dev.rhovas.interpreter.EVALUATOR
import dev.rhovas.interpreter.environment.Object
import dev.rhovas.interpreter.environment.Type
import dev.rhovas.interpreter.parser.rhovas.RhovasAst

object StructInitializer : Library.TypeInitializer("Struct") {

    override fun initialize() {
        inherits.add(Type.ANY)

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

        method("toString",
            returns = Type.STRING,
        ) { (instance) ->
            val instance = instance.value as Map<String, Object>
            Object(Type.STRING, instance.mapValues {
                it.value.methods["toString", listOf()]!!.invoke(listOf()).value as String
            }.toString())
        }
    }

}
