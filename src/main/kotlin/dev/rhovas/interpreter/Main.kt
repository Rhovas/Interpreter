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
import java.io.File

val SCOPE = Scope.Definition(Library.SCOPE)
val ANALYZER = RhovasAnalyzer(SCOPE)
val EVALUATOR = Evaluator(SCOPE)

/**
 * Rhovas main. If given an argument, the argument is the path of a file to be
 * evaluated. Without arguments, enters a REPL environment.
 */
fun main(args: Array<String>) {
    if (args.isNotEmpty()) {
        val input = Input(args[0], File(args[0]).readText())
        eval(input)
    } else {
        while (true) {
            val input = read()
            val print = eval(input)
            print?.let { println(it) }
        }
    }
}

/**
 * Reads input from stdin. This hackily supports multiline input by tracking
 * indentation based on leading/trailing parenthesis/braces/brackets.
 */
fun read(): Input {
    val builder = StringBuilder()
    var indent = 0
    do {
        val line = readLine()!!
        if (line.firstOrNull { it != ' ' } in listOf(')', '}', ']')) {
            indent--
        }
        if (line.lastOrNull { it != ' ' } in listOf('(', '{', '[')) {
            indent++
        }
        builder.append(line)
    } while (indent != 0)
    return Input("REPL", builder.toString())
}

/**
 * Evaluates input and returns an optional string to be printed. For REPL
 * support, the input first attempts to parse as an expression to allow printing
 * raw expressions.
 */
fun eval(input: Input): String? {
    return try {
        val ast = try {
            RhovasParser(input).parse("expression")
        } catch (e: ParseException) {
            RhovasParser(input).parse("source")
        }
        val obj = EVALUATOR.visit(ANALYZER.visit(ast))
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
    }
}
