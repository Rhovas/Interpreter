package dev.rhovas.interpreter.environment

class Object(
    val type: Type,
    val value: Any?,
) {

    companion object {

        val VOID = Object(Type.VOID, null)

    }

    val properties = PropertiesDelegate()
    val methods = MethodsDelegate()

    inner class PropertiesDelegate {

        operator fun get(name: String): Property? {
            return type.methods[name, 0]?.let { getter ->
                Property(name, this@Object, getter, type.methods[name, 1])
            }
        }

    }

    inner class MethodsDelegate {

        operator fun get(name: String, arity: Int): Method? {
            return type.methods[name, arity]?.let { function ->
                Method(name, arity, this@Object, function)
            }
        }

    }

}
