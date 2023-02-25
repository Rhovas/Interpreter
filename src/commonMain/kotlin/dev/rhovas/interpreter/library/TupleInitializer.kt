package dev.rhovas.interpreter.library

import dev.rhovas.interpreter.EVALUATOR
import dev.rhovas.interpreter.environment.Object
import dev.rhovas.interpreter.environment.Type
import dev.rhovas.interpreter.parser.rhovas.RhovasAst

object TupleInitializer : Library.TypeInitializer("Tuple") {

    override fun initialize() {
        generics.add(generic("T", Type.TUPLE.ANY))
        inherits.add(Type.ANY)

        method("equals", operator = "==",
            parameters = listOf("other" to generic("T")),
            returns = Type.BOOLEAN,
        ) { (instance, other) ->
            val instance = instance.value as List<Object>
            val other = other.value as List<Object>
            Object(Type.BOOLEAN, instance.size == other.size && instance.zip(other).all { (instance, other) ->
                val method = instance.methods["==", listOf(instance.type)] ?: throw EVALUATOR.error(
                    null,
                    "Undefined method.",
                    "The method ${instance.type.base.name}.==(${instance.type}) is undefined.",
                )
                if (other.type.isSubtypeOf(method.parameters[0].type)) method.invoke(listOf(other)).value as Boolean else false
            })
        }

        method("to",
            parameters = listOf("type" to Type.TYPE[Type.STRING]),
            returns = Type.STRING,
        ) { (instance) ->
            val instance = instance.value as List<Object>
            Object(Type.STRING, instance.map { it.methods.toString() }.toString())
        }
    }

}
