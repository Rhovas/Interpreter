package dev.rhovas.interpreter.library

import com.ionspin.kotlin.bignum.integer.BigInteger
import dev.rhovas.interpreter.INTERPRETER
import dev.rhovas.interpreter.environment.Object
import dev.rhovas.interpreter.parser.rhovas.RhovasAst

object KernelInitializer: Library.TypeInitializer("Kernel") {

    override fun initialize() {
        function("print",
            parameters = listOf("object" to type("Any")),
        ) { (obj) ->
            INTERPRETER.stdout(obj.methods["toString", listOf()]!!.invoke(listOf()).value as String)
            Object(type("Void"), null)
        }

        function("range",
            parameters = listOf("lower" to type("Integer"), "upper" to type("Integer"), "bound" to type("Atom")),
            returns = type("List", "Integer"),
        ) { (lower, upper, bound) ->
            val lower = lower.value as BigInteger
            val upper = upper.value as BigInteger
            val bound = bound.value as RhovasAst.Atom
            val start = if (bound.name in listOf("incl", "incl_excl")) lower else lower.add(BigInteger.ONE)
            val end = if (bound.name in listOf("incl", "excl_incl")) upper.add(BigInteger.ONE) else upper
            Object(type("List", "Integer"), generateSequence(start.takeIf { it < end }) {
                it.add(BigInteger.ONE).takeIf { it < end }
            }.toList().map { Object(type("Integer"), it) })
        }

        function("lambda",
            generics = listOf(generic("R", type("Dynamic"))),
            parameters = listOf("lambda" to type("Lambda", generic("R", type("Dynamic")))),
            returns = type("Lambda", generic("R", type("Dynamic"))),
        ) { (lambda) ->
            lambda
        }
    }

}
