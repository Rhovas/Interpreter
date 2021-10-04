package dev.rhovas.interpreter.library

import dev.rhovas.interpreter.environment.Function
import dev.rhovas.interpreter.environment.Object
import dev.rhovas.interpreter.environment.Scope
import dev.rhovas.interpreter.evaluator.EvaluateException
import dev.rhovas.interpreter.parser.rhovas.RhovasAst
import java.math.BigInteger

object KernelInitializer {

    fun initialize(scope: Scope) {
        scope.functions.define(Function("lambda", 1) { arguments ->
            if (arguments[0].type != Library.TYPES["Lambda"]!!) {
                throw EvaluateException("Kernel#lambda is not supported with argument ${arguments[0].type.name}")
            }
            arguments[0]
        })
        scope.functions.define(Function("print", 1) { arguments ->
            println(arguments[0].methods["toString", 0]!!.invoke(listOf()).value as String)
            Object(Library.TYPES["Void"]!!, Unit)
        })
        scope.functions.define(Function("range", 3) { arguments ->
            if (arguments[0].type != Library.TYPES["Integer"]!!) {
                throw EvaluateException("Kernel#range is not supported with argument 0 ${arguments[0].type.name}")
            } else if (arguments[1].type != Library.TYPES["Integer"]!!) {
                throw EvaluateException("Kernel#range is not supported with argument 1 ${arguments[1].type.name}")
            } else if (arguments[2].type != Library.TYPES["Atom"]!!) {
                throw EvaluateException("Kernel#range is not supported with argument 2 ${arguments[1].type.name}")
            }
            val lower = arguments[0].value as BigInteger
            val upper = arguments[1].value as BigInteger
            val bound = (arguments[2].value as RhovasAst.Atom).name
            val start = if (bound in listOf("incl", "incl_excl")) lower else lower.add(BigInteger.ONE)
            val end = if (bound in listOf("incl", "excl_incl")) upper.add(BigInteger.ONE) else upper
            Object(Library.TYPES["List"]!!, generateSequence(start.takeIf { it < end }) {
                it.add(BigInteger.ONE).takeIf { it < end }
            }.toList().map { Object(Library.TYPES["Integer"]!!, it) })
        })
    }

}
