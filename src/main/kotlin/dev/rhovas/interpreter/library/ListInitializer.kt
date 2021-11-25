package dev.rhovas.interpreter.library

import dev.rhovas.interpreter.EVALUATOR
import dev.rhovas.interpreter.environment.Object
import dev.rhovas.interpreter.evaluator.EvaluateException
import dev.rhovas.interpreter.evaluator.Evaluator
import java.math.BigInteger

object ListInitializer : Library.TypeInitializer("List") {

    @Reflect.Method("get", operator = "[]", parameters = ["Integer"], returns = "Any")
    fun get(instance: List<Object>, index: BigInteger): Object {
        EVALUATOR.require(index >= BigInteger.ZERO && index < BigInteger.valueOf(instance.size.toLong())) { EVALUATOR.error(
            null,
            "Invalid list index.",
            "Expected an index in range [0, ${instance.size}), but received ${index}.",
        ) }
        return instance[index.toInt()]
    }

    @Reflect.Method("set", operator = "[]=", parameters = ["Integer", "Any"])
    fun set(instance: MutableList<Object>, index: BigInteger, value: Object) {
        EVALUATOR.require(index >= BigInteger.ZERO && index < BigInteger.valueOf(instance.size.toLong())) { EVALUATOR.error(
            null,
            "Invalid list index.",
            "Expected an index in range [0, ${instance.size}), but received ${index}.",
        ) }
        instance[index.toInt()] = value
    }

    @Reflect.Method("concat", operator = "+", parameters = ["List"], returns = "List")
    fun concat(instance: List<Object>, other: List<Object>): List<Object> {
        return instance + other
    }

    @Reflect.Method("for", parameters = ["Lambda"])
    fun for_(instance: List<Object>, lambda: Evaluator.Lambda) {
        EVALUATOR.require(lambda.ast.parameters.isEmpty() || lambda.ast.parameters.size == 1) { EVALUATOR.error(
            lambda.ast,
            "Invalid lambda parameter count.",
            "Function List.for requires a lambda with 1 parameter, but received ${lambda.ast.parameters.size}.",
        ) }
        instance.forEach { lambda.invoke(listOf(Triple("val", Library.TYPES["Any"]!!, it)), Library.TYPES["Any"]!!) }
    }

    @Reflect.Method("map", parameters = ["Lambda"], returns = "List")
    fun map(instance: List<Object>, lambda: Evaluator.Lambda): List<Object> {
        EVALUATOR.require(lambda.ast.parameters.isEmpty() || lambda.ast.parameters.size == 1) { EVALUATOR.error(
            lambda.ast,
            "Invalid lambda parameter count.",
            "Function List.map requires a lambda with 1 parameter, but received ${lambda.ast.parameters.size}.",
        ) }
        return instance.map { lambda.invoke(listOf(Triple("val", Library.TYPES["Any"]!!, it)), Library.TYPES["Any"]!!) }
    }

    @Reflect.Method("equals", operator = "==", parameters = ["List"], returns = "Boolean")
    fun equals(instance: List<Object>, other: List<Object>): Boolean {
        return instance.size == other.size && instance.zip(other).all {
            val method = it.first.methods["==", 1] ?: throw EVALUATOR.error(
                null,
                "Undefined binary operator.",
                "The operator ==/1 (equals) is not defined by type ${it.first.type.name}.",
            )
            if (it.first.type == it.second.type) method.invoke(listOf(it.second)).value as Boolean else false
        }
    }

    @Reflect.Method("toString", returns = "String")
    fun toString(instance: List<Object>): String {
        return instance.map { it.methods["toString", 0]!!.invoke(listOf()).value as String }.toString()
    }

}
