package dev.rhovas.interpreter.analyzer

import dev.rhovas.interpreter.environment.Function
import dev.rhovas.interpreter.environment.Scope
import dev.rhovas.interpreter.parser.Input
import dev.rhovas.interpreter.parser.rhovas.RhovasAst

abstract class Analyzer(protected var scope: Scope) {

    internal var context = Context(null, mutableMapOf(), mutableSetOf())

    data class Context(
        val function: Function.Definition?,
        val labels: MutableMap<String?, Boolean> = mutableMapOf(),
        val jumps: MutableSet<String?> = mutableSetOf(),
    ) {

        private val children = mutableListOf<Context>()

        fun child(): Context {
            return Context(function, labels).also { children.add(it) }
        }

        fun collect(): Context {
            if (children.any { it.jumps.isEmpty() }) {
                jumps.clear()
            } else {
                children.forEach { jumps.addAll(it.jumps) }
            }
            return this.also { children.clear() }
        }

    }

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
