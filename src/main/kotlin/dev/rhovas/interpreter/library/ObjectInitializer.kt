package dev.rhovas.interpreter.library

import dev.rhovas.interpreter.environment.Function
import dev.rhovas.interpreter.environment.Object
import dev.rhovas.interpreter.evaluator.EvaluateException

object ObjectInitializer : Library.TypeInitializer("Object") {

    override fun initialize() {
        type.methods.define(Function("equals", 2) { arguments ->
            val self = arguments[0].value as Map<String, Object>
            val other = arguments[0].value as Map<String, Object>
            if (self.size == other.size && self.keys == other.keys) {
                Object(Library.TYPES["Boolean"]!!, self.keys.all {
                    val method = self[it]!!.methods["==", 1]
                        ?: throw EvaluateException("Binary == is not supported by type ${self[it]!!.type.name}.")
                    if (self[it]!!.type == other[it]!!.type) method.invoke(listOf(other[it]!!)).value as Boolean else false
                })
            } else {
                Object(Library.TYPES["Boolean"]!!, false)
            }
        })
        type.methods.define(type.methods["equals", 2]!!.copy(name = "=="))

        type.methods.define(Function("toString", 1) { arguments ->
            Object(Library.TYPES["String"]!!, (arguments[0].value as Map<String, Object>).mapValues {
                it.value.methods["toString", 0]!!.invoke(listOf()).value.toString()
            })
        })
    }

}
