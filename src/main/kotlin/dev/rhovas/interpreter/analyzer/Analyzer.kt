package dev.rhovas.interpreter.analyzer

import dev.rhovas.interpreter.environment.Scope
import dev.rhovas.interpreter.parser.Input
import dev.rhovas.interpreter.parser.rhovas.RhovasAst

abstract class Analyzer(protected var scope: Scope) {

    fun <T> scoped(scope: Scope, block: () -> T): T {
        val original = this.scope
        this.scope = scope
        try {
            return block()
        } finally {
            this.scope = original
        }
    }

    fun require(condition: Boolean, error: () -> AnalyzeException) {
        if (!condition) {
            throw error()
        }
    }

    fun error(ast: RhovasAst?, summary: String, details: String): AnalyzeException {
        val range = ast?.context?.first() ?: Input.Range(0, 1, 0, 0)
        return AnalyzeException(
            summary,
            details,
            range,
            listOf(), //TODO context
        )
    }

}
