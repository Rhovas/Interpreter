package dev.rhovas.interpreter.analyzer

import dev.rhovas.interpreter.environment.Function
import dev.rhovas.interpreter.parser.Input
import dev.rhovas.interpreter.parser.rhovas.RhovasAst

abstract class Analyzer(internal var context: Context) {

    data class Context(
        val function: Function.Definition?, //TODO: Convert to ContextItem?
        val items: Map<Class<*>, Item<*>>,
    ) {

        private val children = mutableListOf<Context>()

        operator fun <C: Item<T>, T>get(item: Class<C>): T {
            return items[item]!!.value as T
        }

        fun child(): Context {
            return Context(function, items.mapValues { it.value.child() }).also { children.add(it) }
        }

        fun merge() {
            items.forEach { (k, v) ->
                (v as Item<Any?>).merge(children.map { it.items[k]!!.value })
            }
            children.clear()
        }

        abstract class Item<T>(
            val value: T,
        ) {

            abstract fun child(): Item<T>

            abstract fun merge(children: List<T>)

        }

    }

    fun <T> analyze(context: Context? = null, block: () -> T): T {
        val original = this.context
        this.context = context ?: original.child()
        try {
            return block()
        } finally {
            this.context = original.also {
                if (context == null) {
                    it.merge()
                }
            }
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
