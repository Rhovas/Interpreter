package dev.rhovas.interpreter

import dev.rhovas.interpreter.environment.Function
import dev.rhovas.interpreter.environment.Object
import dev.rhovas.interpreter.environment.type.Type
import dev.rhovas.interpreter.environment.Variable
import dev.rhovas.interpreter.library.Library

fun main() {
    val js = Function.Definition(Function.Declaration("js",
        parameters = listOf(Variable.Declaration("literals", Type.LIST[Type.STRING]), Variable.Declaration("arguments", Type.LIST[Type.DYNAMIC])),
        returns = Type.VOID,
    )) {
        val literals = (it[0].value as List<Object>).map { it.value as String }
        val arguments = (it[1].value as List<Object>)
        val source = literals.withIndex().joinToString("") {
            it.value + (arguments.getOrNull(it.index)?.let { JSON.stringify(it) } ?: "")
        }
        try {
            kotlin.js.eval("eval?.(" + JSON.stringify(source) + ")")
        } catch (error: Exception) {
            throw EVALUATOR.error(
                null,
                "DSL Evaluation Error",
                "Failed to eval JavaScript source: ${error.message ?: error}",
            )
        }
        Object(Type.VOID, null)
    }
    Library.SCOPE.functions.define(js)
}
