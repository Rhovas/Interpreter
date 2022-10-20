package dev.rhovas.interpreter

import dev.rhovas.interpreter.environment.Function
import dev.rhovas.interpreter.environment.Object
import dev.rhovas.interpreter.environment.Type
import dev.rhovas.interpreter.environment.Variable
import dev.rhovas.interpreter.library.Library

fun main() {
    val js = Function.Definition(Function.Declaration(
        "js",
        listOf(),
        listOf(
            Variable.Declaration("literals", Type.Reference(Library.TYPES["List"]!!.base, listOf(Library.TYPES["String"]!!)), false),
            Variable.Declaration("arguments", Type.Reference(Library.TYPES["List"]!!.base, listOf(Library.TYPES["Dynamic"]!!)), false),
        ),
        Library.TYPES["Void"]!!,
        listOf(),
    )).also {
        it.implementation = {
            val literals = (it[0].value as List<Object>).map { it.value as String }
            val arguments = (it[1].value as List<Object>)
            val source = literals.withIndex().joinToString("") {
                it.value + (arguments.getOrNull(it.index)?.let { JSON.stringify(it.value) } ?: "")
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
            Object(Library.TYPES["Void"]!!, null)
        }
    }
    Library.SCOPE.functions.define(js)
}
