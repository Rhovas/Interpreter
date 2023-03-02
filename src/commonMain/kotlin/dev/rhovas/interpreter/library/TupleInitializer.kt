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
            Object(Type.BOOLEAN, instance.size == other.size && instance.zip(other).all { it.first.methods.equals(it.second) })
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
