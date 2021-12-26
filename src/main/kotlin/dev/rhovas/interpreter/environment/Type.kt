package dev.rhovas.interpreter.environment

data class Type(
    val name: String,
    private val scope: Scope,
) {

    val variables by scope::variables
    val functions by scope::functions
    val properties = PropertiesDelegate()
    val methods = MethodsDelegate()

    fun isSubtypeOf(type: Type): Boolean {
        return type.name == "Any" || type.name == name
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
            return functions[name, 1 + arity]?.let {
                Function.Method(it.name, it.parameters.subList(1, it.parameters.size), it.returns)
            }
        }

    }

}
