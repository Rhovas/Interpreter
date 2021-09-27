package dev.rhovas.interpreter.library

import dev.rhovas.interpreter.environment.Function
import dev.rhovas.interpreter.environment.Object
import dev.rhovas.interpreter.evaluator.EvaluateException
import dev.rhovas.interpreter.parser.rhovas.RhovasAst

object ObjectInitializer : Library.TypeInitializer("Object") {

    override fun initialize() {
        type.methods.define(Function("get", 2) { arguments ->
            val map = arguments[0].value as MutableMap<String, Object>
            if (arguments[1].type != Library.TYPES["Atom"]!!) {
                throw EvaluateException("List#get is not supported with argument ${arguments[1].type.name}.")
            }
            val key = arguments[1].value as RhovasAst.Atom
            map[key.name] ?: Object(Library.TYPES["Null"]!!, null)
        })
        type.methods.define(type.methods["get", 2]!!.copy(name = "[]"))

        type.methods.define(Function("set", 3) { arguments ->
            val map = arguments[0].value as MutableMap<String, Object>
            if (arguments[1].type != Library.TYPES["Atom"]!!) {
                throw EvaluateException("List#get is not supported with argument ${arguments[1].type.name}.")
            }
            val key = arguments[1].value as RhovasAst.Atom
            map[key.name] = arguments[2]
            Object(Library.TYPES["Void"]!!, Unit)
        })
        type.methods.define(type.methods["set", 3]!!.copy(name = "[]="))

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
            }.toString())
        })
    }

}
