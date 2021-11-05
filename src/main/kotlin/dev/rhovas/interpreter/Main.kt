package dev.rhovas.interpreter

import dev.rhovas.interpreter.evaluator.EvaluateException
import dev.rhovas.interpreter.evaluator.Evaluator
import dev.rhovas.interpreter.library.Library
import dev.rhovas.interpreter.parser.Input
import dev.rhovas.interpreter.parser.ParseException
import dev.rhovas.interpreter.parser.rhovas.RhovasParser
import java.io.File

fun main(args: Array<String>) {
    try {
        val input = Input(args[0], "{${File(args[0]).readText()}}")
        val ast = RhovasParser(input).parse("statement")
        Library.initialize()
        Evaluator(Library.SCOPE).visit(ast)
    } catch (e: ParseException) {
        println(e.message)
    } catch (e: EvaluateException) {
        println(e.message)
    } catch (e: Exception) {
        e.printStackTrace()
    }
}
