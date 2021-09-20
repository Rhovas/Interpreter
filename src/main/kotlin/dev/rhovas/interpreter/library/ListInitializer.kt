package dev.rhovas.interpreter.library

import dev.rhovas.interpreter.environment.Function
import dev.rhovas.interpreter.environment.Object
import dev.rhovas.interpreter.evaluator.EvaluateException

object ListInitializer : Library.TypeInitializer("List") {

    override fun initialize() {
        type.methods.define(Function("concat", 2) { arguments ->
            if (arguments[1].type != type) {
                throw EvaluateException("List#concat is not supported with argument ${arguments[1].type.name}")
            }
            Object(Library.TYPES["List"]!!, arguments[0].value as List<Object> + arguments[1].value as List<Object>)
        })
        type.methods.define(type.methods["concat", 2]!!.copy(name = "+"))

        type.methods.define(Function("equals", 2) { arguments ->
            val self = arguments[0].value as List<Object>
            val other = arguments[0].value as List<Object>
            if (self.size == other.size) {
                Object(Library.TYPES["Boolean"]!!, self.zip(other).all {
                    val method = it.first.methods["==", 1]
                        ?: throw EvaluateException("Binary == is not supported by type ${it.first.type.name}.")
                    if (it.first.type == it.second.type) method.invoke(listOf(it.second)).value as Boolean else false
                })
            } else {
                Object(Library.TYPES["Boolean"]!!, false)
            }
        })
        type.methods.define(type.methods["equals", 2]!!.copy(name = "=="))

        type.methods.define(Function("toString", 1) { arguments ->
            Object(Library.TYPES["String"]!!, (arguments[0].value as List<Object>).map {
                it.methods["toString", 0]!!.invoke(listOf()).value.toString()
            })
        })
    }

}
