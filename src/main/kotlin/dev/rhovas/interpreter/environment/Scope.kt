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

    }

    inner class FunctionsDelegate {

        private val functions = mutableMapOf<Pair<String, Int>, Function>()

        operator fun get(name: String, arity: Int): Function? {
            return functions[Pair(name, arity)] ?: parent?.functions?.get(name, arity)
        }

        fun define(function: Function) {
            functions[Pair(function.name, function.parameters.size)] = function
        }

    }

}
