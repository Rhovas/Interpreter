package dev.rhovas.interpreter.library

import dev.rhovas.interpreter.EVALUATOR
import dev.rhovas.interpreter.environment.Object
import dev.rhovas.interpreter.evaluator.EvaluateException
import dev.rhovas.interpreter.parser.rhovas.RhovasAst

object ObjectInitializer : Library.TypeInitializer("Object") {

    @Reflect.Method("get", operator = "[]", parameters = ["Atom"], returns = "Any")
    fun get(instance: Map<String, Object>, key: RhovasAst.Atom): Object {
        return instance[key.name] ?: Object(Library.TYPES["Null"]!!, null)
    }

    @Reflect.Method("set", operator = "[]=", parameters = ["Atom", "Any"])
    fun get(instance: MutableMap<String, Object>, key: RhovasAst.Atom, value: Object) {
        instance[key.name] = value
    }

    @Reflect.Method("equals", operator = "==", parameters = ["Object"], returns = "Boolean")
    fun equals(instance: Map<String, Object>, other: Map<String, Object>): Boolean {
        return instance.keys == other.keys && instance.keys.all {
            val method = instance[it]!!.methods["==", 1] ?: throw EVALUATOR.error(
                null,
                "Undefined binary operator.",
                "The operator ==/1 (equals) is not defined by type ${instance[it]!!.type.name}.",
            )
            if (instance[it]!!.type == other[it]!!.type) method.invoke(listOf(other[it]!!)).value as Boolean else false
        }
    }

    @Reflect.Method("toString", returns = "String")
    fun toString(instance: Map<String, Object>): String {
        return instance.mapValues { it.value.methods["toString", 0]!!.invoke(listOf()).value as String }.toString()
    }

}
