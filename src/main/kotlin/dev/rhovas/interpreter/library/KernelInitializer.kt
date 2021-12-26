package dev.rhovas.interpreter.library

import dev.rhovas.interpreter.environment.Object
import dev.rhovas.interpreter.evaluator.Evaluator
import dev.rhovas.interpreter.parser.rhovas.RhovasAst
import java.math.BigInteger

object KernelInitializer: Library.TypeInitializer("Kernel", Library.SCOPE) {

    @Reflect.Function("print", parameters = ["Any"])
    fun print(obj: Object) {
        println(obj.methods["toString", 0]!!.invoke(listOf()).value as String)
    }

    @Reflect.Function("range", parameters = ["Integer", "Integer", "Atom"], "List")
    fun range(lower: BigInteger, upper: BigInteger, bound: RhovasAst.Atom): List<Object> {
        val start = if (bound.name in listOf("incl", "incl_excl")) lower else lower.add(BigInteger.ONE)
        val end = if (bound.name in listOf("incl", "excl_incl")) upper.add(BigInteger.ONE) else upper
        return generateSequence(start.takeIf { it < end }) {
            it.add(BigInteger.ONE).takeIf { it < end }
        }.toList().map { Object(Library.TYPES["Integer"]!!, it) }
    }

    @Reflect.Function("lambda", parameters = ["Lambda"], "Lambda")
    fun lambda(lambda: Evaluator.Lambda): Evaluator.Lambda {
        return lambda
    }

}
