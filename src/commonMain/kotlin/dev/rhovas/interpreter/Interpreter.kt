package dev.rhovas.interpreter

import dev.rhovas.interpreter.analyzer.AnalyzeException
import dev.rhovas.interpreter.analyzer.rhovas.RhovasAnalyzer
import dev.rhovas.interpreter.environment.Scope
import dev.rhovas.interpreter.evaluator.EvaluateException
import dev.rhovas.interpreter.evaluator.Evaluator
import dev.rhovas.interpreter.library.Library
import dev.rhovas.interpreter.parser.Input
import dev.rhovas.interpreter.parser.ParseException
import dev.rhovas.interpreter.parser.rhovas.RhovasAst
import dev.rhovas.interpreter.parser.rhovas.RhovasParser

var INTERPRETER = Interpreter()
val EVALUATOR: Evaluator get() = INTERPRETER.evaluator

class Interpreter(
    val stdin: () -> String = ::readln,
    val stdout: (String) -> Unit = ::println,
) {

    val scope = Scope.Definition(Library.SCOPE)
    val analyzer = RhovasAnalyzer(scope)
    val evaluator = Evaluator(scope)

    /**
     * Evaluates input and returns an optional string to be printed. For REPL
     * support, the input attempts to parse as an expression if source fails to
     * allow printing raw expressions.
     *
     * Note: This also updates the global `INTERPRETER` variable, since standard
     * library functions need access to the current evaluator and nothing better
     * has been set up yet.
     */
    fun eval(input: Input): String? {
        val original = INTERPRETER
        return try {
            INTERPRETER = this
            val ast = try {
                RhovasParser(input).parse("source")
            } catch (e: ParseException) {
                try {
                    RhovasParser(input).parse("expression")
                } catch (ignored: ParseException) {
                    throw e //use the original exception
                }
            }
            val obj = evaluator.visit(analyzer.visit(ast))
            when (ast) {
                is RhovasAst.Expression -> obj.methods["toString", listOf()]!!.invoke(listOf()).value as String
                else -> null
            }
        } catch (e: ParseException) {
            input.diagnostic(e.summary, e.details, e.range, e.context)
        } catch (e: AnalyzeException) {
            input.diagnostic(e.summary, e.details, e.range, e.context)
        } catch (e: EvaluateException) {
            input.diagnostic(e.summary, e.details, e.range, e.context)
        } catch (e: Exception) {
            "Unexpected exception: " + e.message + "\n\n${e.stackTraceToString()}"
        } finally {
            INTERPRETER = original
        }
    }

}
