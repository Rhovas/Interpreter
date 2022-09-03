package dev.rhovas.interpreter.analyzer

import dev.rhovas.interpreter.parser.Input
import dev.rhovas.interpreter.parser.rhovas.RhovasAst
import kotlin.reflect.KClass

abstract class Analyzer(internal var context: Context) {

    protected val Context.inputs get() = this[InputContext::class]

    data class Context(
        val items: Map<String, Item<*>>,
    ) {

        private val children = mutableListOf<Context>()

        operator fun <C: Item<T>, T>get(item: KClass<C>): T {
            return items[item.simpleName]!!.value as T
        }

        fun child(): Context {
            return Context(items.mapValues { it.value.child() }).also { children.add(it) }
        }

        fun merge() {
            items.forEach { (k, v) ->
                (v as Item<Any?>).merge(children.map { it.items[k]!!.value })
            }
            children.clear()
        }

        fun with(vararg items: Item<*>): Context {
            return Context(this.items.toMutableMap().also { it.putAll(items.associateBy { it::class.simpleName!! }) })
        }

        abstract class Item<T>(
            val value: T,
        ) {

            abstract fun child(): Item<T>

            abstract fun merge(children: List<T>)

        }

    }

    data class InputContext(
        val inputs: ArrayDeque<Input.Range>,
    ) : Context.Item<ArrayDeque<Input.Range>>(inputs) {

        override fun child(): InputContext {
            return this
        }

        override fun merge(children: List<ArrayDeque<Input.Range>>) {}

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

    fun error(ast: RhovasAst, summary: String, details: String): AnalyzeException {
        return error(summary, details, ast.context?.first() ?: Input.Range(0, 1, 0, 0))
    }

    fun error(summary: String, details: String, range: Input.Range): AnalyzeException {
        return AnalyzeException(summary, details, range, context.inputs)
    }

}
