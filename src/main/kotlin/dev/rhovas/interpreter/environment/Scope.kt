package dev.rhovas.interpreter.environment

sealed class Scope<V: Variable, F: Function>(private val parent: Scope<out V, out F>?) {

    val variables = VariablesDelegate<V>()
    val functions = FunctionsDelegate<F>()
    val types = TypesDelegate()

    class Declaration(parent: Scope<out Variable, out Function>?): Scope<Variable, Function>(parent)

    class Definition(parent: Definition?): Scope<Variable.Definition, Function.Definition>(parent)

    inner class VariablesDelegate<V: Variable> {

        private val variables = mutableMapOf<String, V>()

        operator fun get(name: String): V? {
            return variables[name] ?: (parent as Scope<V, *>?)?.variables?.get(name)
        }

        fun isDefined(name: String, current: Boolean): Boolean {
            return variables.containsKey(name) || !current && parent?.variables?.isDefined(name, current) ?: false
        }

        fun define(variable: V) {
            variables[variable.name] = variable
        }

        internal fun collect(): MutableMap<String, V> {
            val map = (parent as Scope<V, *>?)?.variables?.collect() ?: mutableMapOf()
            map.putAll(variables)
            return map
        }

    }

    inner class FunctionsDelegate<F: Function> {

        private val functions = mutableMapOf<Pair<String, Int>, MutableList<F>>()

        operator fun get(name: String, arity: Int): List<F> {
            //TODO: Overriding
            return functions[Pair(name, arity)] ?: (parent as Scope<*, F>?)?.functions?.get(name, arity) ?: listOf()
        }

        operator fun get(name: String, arguments: List<Type>): F? {
            val candidates = get(name, arguments.size).mapNotNull { function ->
                val generics = mutableMapOf<String, Type>()
                function.takeIf {
                    arguments.zip(function.parameters)
                        .all { zip -> zip.first.isSubtypeOf(zip.second.second, generics) }
                }?.bind(generics) as F?
            }
            return when (candidates.size) {
                0 -> null
                1 -> candidates[0]
                else -> throw AssertionError()
            }
        }

        fun isDefined(name: String, arity: Int, current: Boolean): Boolean {
            return functions.containsKey(Pair(name, arity)) || !current && parent?.variables?.isDefined(name, current) ?: false
        }

        fun define(function: F, alias: String = function.name) {
            functions.getOrPut(Pair(alias, function.parameters.size), ::mutableListOf).add(function)
        }

        internal fun collect(): MutableMap<Pair<String, Int>, List<F>> {
            //TODO
            val map = (parent as Scope<*, F>?)?.functions?.collect() ?: mutableMapOf()
            map.putAll(functions)
            return map
        }

    }

    inner class TypesDelegate {

        private val types = mutableMapOf<String, Type>()

        operator fun get(name: String): Type? {
            return types[name] ?: parent?.types?.get(name)
        }

        fun isDefined(name: String, current: Boolean): Boolean {
            return types.containsKey(name) || !current && parent?.types?.isDefined(name, current) ?: false
        }

        fun define(type: Type, alias: String = type.base.name) {
            types[alias] = type
        }

        internal fun collect(): MutableMap<String, Type> {
            val map = parent?.types?.collect() ?: mutableMapOf()
            map.putAll(types)
            return map
        }

    }

    override fun toString(): String {
        return "Scope(" +
                "parent=${parent?.let { "Scope@${it.hashCode().toString(16)}" }}, " +
                "variables=${variables.collect()}, " +
                "functions=${functions.collect()}" +
                "types=${types.collect()}" +
                ")"
    }


}
