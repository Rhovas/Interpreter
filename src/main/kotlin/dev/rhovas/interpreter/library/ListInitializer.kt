package dev.rhovas.interpreter.library

import dev.rhovas.interpreter.EVALUATOR
import dev.rhovas.interpreter.environment.Object
import dev.rhovas.interpreter.evaluator.EvaluateException
import dev.rhovas.interpreter.evaluator.Evaluator
import java.math.BigInteger

object ListInitializer : Library.TypeInitializer("List") {

    @Reflect.Property("size", type = "Integer")
    fun size(instance: List<Object>): BigInteger {
        return instance.size.toBigInteger()
    }

    @Reflect.Property("first", type = "Any")
    fun first(instance: List<Object>): Object {
        return instance.firstOrNull() ?: Object(Library.TYPES["Null"]!!, null)
    }

    @Reflect.Property("last", type = "Any")
    fun last(instance: List<Object>): Object {
        return instance.lastOrNull() ?: Object(Library.TYPES["Null"]!!, null)
    }

    @Reflect.Method("get", operator = "[]", parameters = ["Integer"], returns = "Any")
    fun get(instance: List<Object>, index: BigInteger): Object {
        EVALUATOR.require(index >= BigInteger.ZERO && index < instance.size.toBigInteger()) { EVALUATOR.error(
            null,
            "Invalid list index.",
            "Expected an index in range [0, ${instance.size}), but received ${index}.",
        ) }
        return instance[index.toInt()]
    }

    @Reflect.Method("set", operator = "[]=", parameters = ["Integer", "Any"])
    fun set(instance: MutableList<Object>, index: BigInteger, value: Object) {
        EVALUATOR.require(index >= BigInteger.ZERO && index < instance.size.toBigInteger()) { EVALUATOR.error(
            null,
            "Invalid list index.",
            "Expected an index in range [0, ${instance.size}), but received ${index}.",
        ) }
        instance[index.toInt()] = value
    }

    @Reflect.Method("slice", parameters = ["Integer"], returns = "List")
    fun slice(instance: List<Object>, start: BigInteger): List<Object> {
        return slice(instance, start, instance.size.toBigInteger())
    }

    @Reflect.Method("slice", parameters = ["Integer", "Integer"], returns = "List")
    fun slice(instance: List<Object>, start: BigInteger, end: BigInteger): List<Object> {
        //TODO: Consider supporting negative indices
        EVALUATOR.require(start >= BigInteger.ZERO && start <= instance.size.toBigInteger()) { EVALUATOR.error(
            null,
            "Invalid index.",
            "Expected a start index in range [0, ${instance.size}), but received ${start}.",
        ) }
        EVALUATOR.require(end >= start && end <= instance.size.toBigInteger()) { EVALUATOR.error(
            null,
            "Invalid index.",
            "Expected an end index in range [start = ${start}, ${instance.size}), but received ${end}.",
        ) }
        return instance.subList(start.toInt(), end.toInt())
    }

    @Reflect.Method("contains", parameters = ["Any"], returns = "List")
    fun contains(instance: List<Object>, value: Object): Boolean {
        val method = value.methods["==", 1] ?: throw EVALUATOR.error(
            null,
            "Undefined binary operator.",
            "The binary operator == (equals) is not defined by type ${value.type.name}.",
        )
        return instance.any {
            it.type.isSubtypeOf(method.function.parameters[0]) && method.invoke(value, listOf(it)).value as Boolean
        }
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

    @Reflect.Method("filter", parameters = ["Lambda"], returns = "List")
    fun filter(instance: List<Object>, lambda: Evaluator.Lambda): List<Object> {
        EVALUATOR.require(lambda.ast.parameters.isEmpty() || lambda.ast.parameters.size == 1) { EVALUATOR.error(
            lambda.ast,
            "Invalid lambda parameter count.",
            "Function List.filter requires a lambda with 1 parameter, but received ${lambda.ast.parameters.size}.",
        ) }
        return instance.filter {
            val result = lambda.invoke(listOf(Triple("val", Library.TYPES["Any"]!!, it)), Library.TYPES["Any"]!!)
            EVALUATOR.require(result.type.isSubtypeOf(Library.TYPES["Boolean"]!!)) { EVALUATOR.error(
                lambda.ast,
                "Invalid lambda result.",
                "Function List.filter requires the lambda result to be type Boolean, but received ${result.type.name}.",
            ) }
            result.value as Boolean
        }
    }

    @Reflect.Method("reduce", parameters = ["Lambda"], returns = "Any")
    fun reduce(instance: List<Object>, lambda: Evaluator.Lambda): Object {
        EVALUATOR.require(lambda.ast.parameters.isEmpty() || lambda.ast.parameters.size == 2) { EVALUATOR.error(
            lambda.ast,
            "Invalid lambda parameter count.",
            "Function List.filter requires a lambda with 2 parameter, but received ${lambda.ast.parameters.size}.",
        ) }
        return instance.reduceOrNull { result, element ->
            lambda.invoke(listOf(
                Triple("result", Library.TYPES["Any"]!!, result),
                Triple("element", Library.TYPES["Any"]!!, element),
            ), Library.TYPES["Any"]!!)
        } ?: Object(Library.TYPES["Null"]!!, null)
    }

    @Reflect.Method("reduce", parameters = ["Any", "Lambda"], returns = "Any")
    fun reduce(instance: List<Object>, initial: Object, lambda: Evaluator.Lambda): Object {
        EVALUATOR.require(lambda.ast.parameters.isEmpty() || lambda.ast.parameters.size == 2) { EVALUATOR.error(
            lambda.ast,
            "Invalid lambda parameter count.",
            "Function List.filter requires a lambda with 2 parameter, but received ${lambda.ast.parameters.size}.",
        ) }
        return instance.fold(initial) { result, element ->
            lambda.invoke(listOf(
                Triple("result", Library.TYPES["Any"]!!, result),
                Triple("element", Library.TYPES["Any"]!!, element),
            ), Library.TYPES["Any"]!!)
        }
    }

    @Reflect.Method("equals", operator = "==", parameters = ["List"], returns = "Boolean")
    fun equals(instance: List<Object>, other: List<Object>): Boolean {
        return instance.size == other.size && instance.zip(other).all {
            val method = it.first.methods["==", 1] ?: throw EVALUATOR.error(
                null,
                "Undefined binary operator.",
                "The operator ==/1 (equals) is not defined by type ${it.first.type.name}.",
            )
            if (it.first.type == it.second.type) method.invoke(it.first, listOf(it.second)).value as Boolean else false
        }
    }

    @Reflect.Method("toString", returns = "String")
    fun toString(instance: List<Object>): String {
        return instance.map { it.methods["toString", 0]!!.invoke(it, listOf()).value as String }.toString()
    }

}
