package dev.rhovas.interpreter.library

import dev.rhovas.interpreter.EVALUATOR
import dev.rhovas.interpreter.environment.Object
import dev.rhovas.interpreter.parser.rhovas.RhovasAst

@Reflect.Type("Object")
object ObjectInitializer : Library.TypeInitializer("Object") {

    override fun initialize() {
        inherits.add(Library.TYPES["Any"]!!)
    }

    @Reflect.Method("get", operator = "[]",
        parameters = [Reflect.Type("Atom")],
        returns = Reflect.Type("Dynamic")
    )
    fun get(instance: Map<String, Object>, key: RhovasAst.Atom): Object {
        return instance[key.name] ?: Object(Library.TYPES["Null"]!!, null)
    }

    @Reflect.Method("set", operator = "[]=",
        parameters = [Reflect.Type("Atom"), Reflect.Type("Dynamic")],
    )
    fun set(instance: MutableMap<String, Object>, key: RhovasAst.Atom, value: Object) {
        instance[key.name] = value
    }

    @Reflect.Method("equals", operator = "==",
        parameters = [Reflect.Type("Object")],
        returns = Reflect.Type("Boolean"),
    )
    fun equals(instance: Map<String, Object>, other: Map<String, Object>): Boolean {
        return instance.keys == other.keys && instance.keys.all {
            val method = instance[it]!!.methods["==", listOf(instance[it]!!.type)] ?: throw EVALUATOR.error(
                null,
                "Undefined method.",
                "The method ${instance[it]!!.type.base.name}.==(${instance[it]!!.type}) is undefined.",
            )
            if (instance[it]!!.type == other[it]!!.type) method.invoke(listOf(other[it]!!)).value as Boolean else false
        }
    }

    @Reflect.Method("toString", returns = Reflect.Type("String"))
    fun toString(instance: Map<String, Object>): String {
        return instance.mapValues { it.value.methods["toString", listOf()]!!.invoke(listOf()).value as String }.toString()
    }

}
