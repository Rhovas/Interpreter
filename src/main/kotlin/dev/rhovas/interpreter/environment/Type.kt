package dev.rhovas.interpreter.environment

import dev.rhovas.interpreter.library.Library

data class Type(
    val name: String,
    private val scope: Scope,
) {

    val variables by scope::variables
    val functions by scope::functions
    val properties = PropertiesDelegate()
    val methods = MethodsDelegate()

    fun isSubtypeOf(type: Type): Boolean {
        return name == type.name || type.name == "Any" || name == "Dynamic" || type.name == "Dynamic"
    }

    override fun toString(): String {
        return "Type(name='$name', scope=Scope@${scope.hashCode().toString(16)})"
    }

    inner class PropertiesDelegate {

        operator fun get(name: String): Variable.Property? {
            return methods[name, 0]?.let {
                Variable.Property(it, methods[name, 1])
            }
        }

    }

    inner class MethodsDelegate {

        operator fun get(name: String, arity: Int): Function.Method? {
            return if (this@Type.name == "Dynamic") {
                Function.Method(name, 0.until(arity).map { Pair("val_${it}", this@Type) }, this@Type)
            } else {
                functions[name, 1 + arity]?.let {
                    Function.Method(it.name, it.parameters.subList(1, it.parameters.size), it.returns)
                }
            }
        }

    }

}
