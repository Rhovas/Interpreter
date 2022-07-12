package dev.rhovas.interpreter.environment

class Scope(private val parent: Scope?) {

    val variables = VariablesDelegate()
    val functions = FunctionsDelegate()
    val types = TypesDelegate()

    inner class VariablesDelegate {

        private val variables = mutableMapOf<String, Variable>()

        operator fun get(name: String): Variable? {
            return variables[name] ?: parent?.variables?.get(name)
        }

        fun isDefined(name: String, current: Boolean): Boolean {
            return variables.containsKey(name) || !current && parent?.variables?.isDefined(name, current) ?: false
        }

        fun define(variable: Variable) {
            variables[variable.name] = variable
        }

        internal fun collect(): MutableMap<String, Variable> {
            val map = parent?.variables?.collect() ?: mutableMapOf()
            map.putAll(variables)
            return map
        }

    }

    inner class FunctionsDelegate {

        private val functions = mutableMapOf<Pair<String, Int>, MutableList<Function>>()

        private fun get(name: String, arity: Int): List<Function> {
            //TODO: Overriding
            return functions[Pair(name, arity)] ?: parent?.functions?.get(name, arity) ?: listOf()
        }

        operator fun get(name: String, arguments: List<Type>): Function? {
            val candidates = get(name, arguments.size).mapNotNull { function ->
                val generics = mutableMapOf<String, Type>()
                arguments.zip(function.parameters)
                    .all { zip -> zip.first.isSubtypeOf(zip.second.second, generics) }
                    .takeIf { it }?.let {
                        Function.Definition(
                            function.name,
                            function.generics.map { Type.Generic(it.name, it.bind(generics)) },
                            function.parameters.map { Pair(it.first, it.second.bind(generics)) },
                            function.returns.bind(generics),
                            function.throws,
                        ).also {
                            it.implementation = { (function as Function.Definition).implementation.invoke(it) }
                        }
                    }
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

        fun define(function: Function) {
            //TODO: Restrict to Function.Definition
            functions.getOrPut(Pair(function.name, function.parameters.size), ::mutableListOf).add(function)
        }

        internal fun collect(): MutableMap<Pair<String, Int>, List<Function>> {
            //TODO
            val map = parent?.functions?.collect() ?: mutableMapOf()
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
