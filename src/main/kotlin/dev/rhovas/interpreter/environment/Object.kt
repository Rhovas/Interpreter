package dev.rhovas.interpreter.environment

data class Object(
    val type: Type,
    val value: Any?,
) {

    val properties = PropertiesDelegate()
    val methods = MethodsDelegate()

    inner class PropertiesDelegate {

        operator fun get(name: String): Property? {
            return type.methods[name, 1]?.let { Property(Method(it), type.methods[name, 2]?.let { Method(it) }) }
        }

    }

    inner class MethodsDelegate {

        operator fun get(name: String, arity: Int): Method? {
            return type.methods[name, arity + 1]?.let { Method(it) }
        }

    }

}
