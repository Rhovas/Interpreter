package dev.rhovas.interpreter.environment

import dev.rhovas.interpreter.library.Library

sealed class Scope<V: Variable, F: Function>(private val parent: Scope<out V, out F>?) {

    val variables = VariablesDelegate<V>()
    val functions = FunctionsDelegate<F>()
    val types = TypesDelegate()

    class Declaration(parent: Scope<out Variable, out Function>?): Scope<Variable, Function>(parent)

    class Definition(parent: Definition?): Scope<Variable.Definition, Function.Definition>(parent)

    inner class VariablesDelegate<V: Variable> {

        private val variables = mutableMapOf<String, V>()

        operator fun get(name: String, current: Boolean = false): V? {
            return variables[name] ?: when {
                current -> null
                else -> (parent as Scope<V, *>?)?.variables?.get(name)
            }
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

        operator fun get(name: String, arity: Int, current: Boolean = false): List<F> {
            return (functions[Pair(name, arity)] ?: listOf()) + when {
                current -> listOf()
                else -> ((parent as Scope<*, F>?)?.functions?.get(name, arity) ?: listOf())
            }
        }

        operator fun get(name: String, arguments: List<Type>, current: Boolean = false): F? {
            return get(name, arguments.size, current).firstNotNullOfOrNull { function ->
                val generics = mutableMapOf<String, Type>()
                function.takeIf {
                    arguments.zip(function.parameters)
                        .all { zip -> zip.first.isSubtypeOf(zip.second.type, generics) }
                }?.bind(generics.mapValues {
                    when (val type = it.value) {
                        is Type.Variant -> type.upper ?: type.lower ?: Library.TYPES["Any"]!!
                        else -> type
                    }
                }) as F?
            }
        }

        fun define(function: F, alias: String = function.name) {
            val overloads = functions.getOrPut(Pair(alias, function.parameters.size), ::mutableListOf)
            require(overloads.all { it.isDisjointWith(function) })
            overloads.add(function)
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
