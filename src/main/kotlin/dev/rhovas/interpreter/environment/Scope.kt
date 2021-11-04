package dev.rhovas.interpreter.environment

class Scope(private val parent: Scope?) {

    val variables = VariablesDelegate()
    val functions = FunctionsDelegate()

    inner class VariablesDelegate {

        private val variables = mutableMapOf<String, Variable>()

        operator fun get(name: String): Variable? {
            return variables[name] ?: parent?.variables?.get(name)
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

        private val functions = mutableMapOf<Pair<String, Int>, Function>()

        operator fun get(name: String, arity: Int): Function? {
            return functions[Pair(name, arity)] ?: parent?.functions?.get(name, arity)
        }

        fun define(function: Function) {
            functions[Pair(function.name, function.parameters.size)] = function
        }

        internal fun collect(): MutableMap<Pair<String, Int>, Function> {
            val map = parent?.functions?.collect() ?: mutableMapOf()
            map.putAll(functions)
            return map
        }

    }

    override fun toString(): String {
        return "Scope(" +
                "parent=${parent?.let { "Scope@${it.hashCode().toString(16)}" }}, " +
                "variables=${variables.collect()}, " +
                "functions=${functions.collect()}" +
                ")"
    }


}
