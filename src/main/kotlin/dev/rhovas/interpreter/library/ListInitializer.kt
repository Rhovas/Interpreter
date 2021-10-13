package dev.rhovas.interpreter.library

import dev.rhovas.interpreter.environment.Object
import dev.rhovas.interpreter.evaluator.EvaluateException
import dev.rhovas.interpreter.evaluator.Evaluator
import java.math.BigInteger

object ListInitializer : Library.TypeInitializer("List") {

    @Reflect.Method("get", operator = "[]", parameters = ["Integer"], returns = "")
    fun get(instance: List<Object>, index: BigInteger): Object {
        if (index < BigInteger.ZERO || index >= BigInteger.valueOf(instance.size.toLong())) {
            throw EvaluateException("Index $index out of bounds for list of size ${instance.size}.")
        }
        return instance[index.toInt()]
    }

    @Reflect.Method("set", operator = "[]=", parameters = ["Integer", ""])
    fun set(instance: MutableList<Object>, index: BigInteger, value: Object) {
        if (index < BigInteger.ZERO || index >= BigInteger.valueOf(instance.size.toLong())) {
            throw EvaluateException("Index $index out of bounds for list of size ${instance.size}.")
        }
        instance[index.toInt()] = value
    }

    @Reflect.Method("concat", operator = "+", parameters = ["List"], returns = "List")
    fun concat(instance: List<Object>, other: List<Object>): List<Object> {
        return instance + other
    }

    @Reflect.Method("for", parameters = ["Lambda"])
    fun for_(instance: List<Object>, lambda: Evaluator.Lambda) {
        if (lambda.ast.parameters.isNotEmpty() && lambda.ast.parameters.size != 1) {
            throw EvaluateException("List#for requires a lambda with one parameter.")
        }
        instance.forEach { lambda.invoke(mapOf(Pair("val", it))) }
    }

    @Reflect.Method("map", parameters = ["Lambda"], returns = "List")
    fun map(instance: List<Object>, lambda: Evaluator.Lambda): List<Object> {
        if (lambda.ast.parameters.isNotEmpty() && lambda.ast.parameters.size != 1) {
            throw EvaluateException("List#map requires a lambda with one parameter.")
        }
        return instance.map { lambda.invoke(mapOf(Pair("val", it))) }
    }

    @Reflect.Method("equals", operator = "==", parameters = ["List"], returns = "Boolean")
    fun equals(instance: List<Object>, other: List<Object>): Boolean {
        return instance.size == other.size && instance.zip(other).all {
            val method = it.first.methods["==", 1]
                ?: throw EvaluateException("Binary == is not supported by type ${it.first.type.name}.")
            if (it.first.type == it.second.type) method.invoke(listOf(it.second)).value as Boolean else false
        }
    }

    @Reflect.Method("toString", returns = "String")
    fun toString(instance: List<Object>): String {
        return instance.map { it.methods["toString", 0]!!.invoke(listOf()).value as String }.toString()
    }

}
