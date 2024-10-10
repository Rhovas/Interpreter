package dev.rhovas.interpreter.environment

import dev.rhovas.interpreter.environment.type.Type

sealed class Scope<VI: VO, VO: Variable, FI: FO, FO: Function>(
    private val parent: Scope<*, out VO, *, out FO>?,
) {

    val variables = VariablesDelegate()
    val functions = FunctionsDelegate()
    val types = TypesDelegate()

    class Declaration(parent: Scope<*, out Variable, *, out Function>?): Scope<Variable, Variable, Function, Function>(parent)

    class Definition(parent: Scope<*, out Variable.Definition, *, out Function.Definition>?): Scope<Variable.Definition, Variable.Definition, Function.Definition, Function.Definition>(parent)

    inner class VariablesDelegate {

        private val variables = mutableMapOf<String, VO>()

        operator fun get(name: String, current: Boolean = false): VO? {
            return variables[name] ?: when {
                current -> null
                else -> (parent as Scope<*, out VO, *, *>?)?.variables?.get(name)
            }
        }

        fun define(variable: VI) {
            require(!variables.containsKey(variable.name))
            variables[variable.name] = variable
        }

        internal fun collect(): MutableMap<String, VO> {
            val map = (parent as Scope<*, VO, *, *>?)?.variables?.collect() ?: mutableMapOf()
            map.putAll(variables)
            return map
        }

    }

    inner class FunctionsDelegate {

        private val functions = mutableMapOf<Pair<String, Int>, MutableList<FO>>()

        operator fun get(name: String, arity: Int, current: Boolean = false): List<FO> {
            return (functions[Pair(name, arity)] ?: listOf()) + when {
                current -> listOf()
                else -> ((parent as Scope<*, *, *, FO>?)?.functions?.get(name, arity) ?: listOf())
            }
        }

        operator fun get(name: String, arguments: List<Type>, current: Boolean = false): FO? {
            return get(name, arguments.size, current).firstNotNullOfOrNull { function ->
                val generics = mutableMapOf<String, Type>()
                function.takeIf { it.isResolvedBy(arguments, generics) }?.bind(generics) as FO?
            }
        }

        fun define(function: FI, alias: String = function.name) {
            val overloads = functions.getOrPut(Pair(alias, function.parameters.size), ::mutableListOf)
            require(overloads.all { it.isDisjointWith(function) })
            overloads.add(function)
        }

        internal fun collect(): MutableMap<Pair<String, Int>, List<FO>> {
            val map = (parent as Scope<*, *, *, FO>?)?.functions?.collect() ?: mutableMapOf()
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

        fun define(name: String, type: Type) {
            require(!types.containsKey(name))
            types[name] = type
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
