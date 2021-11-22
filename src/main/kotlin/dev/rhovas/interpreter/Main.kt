package dev.rhovas.interpreter

import dev.rhovas.interpreter.environment.Scope
import dev.rhovas.interpreter.evaluator.EvaluateException
import dev.rhovas.interpreter.evaluator.Evaluator
import dev.rhovas.interpreter.library.Library
import dev.rhovas.interpreter.parser.Input
import dev.rhovas.interpreter.parser.ParseException
import dev.rhovas.interpreter.parser.rhovas.RhovasParser
import java.io.File

val EVALUATOR = Evaluator(Scope(Library.SCOPE))

fun main(args: Array<String>) {
    val input = Input(args[0], File(args[0]).readText())
    try {
        val ast = RhovasParser(input).parse("source")
        Library.initialize()
        EVALUATOR.visit(ast)
    } catch (e: ParseException) {
        println(input.diagnostic(e.summary, e.details, e.range, e.context))
    } catch (e: EvaluateException) {
        println(input.diagnostic(e.summary, e.details, e.range, e.context))
    }
}
