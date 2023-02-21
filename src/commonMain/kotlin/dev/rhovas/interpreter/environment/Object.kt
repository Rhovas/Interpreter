package dev.rhovas.interpreter.environment

data class Object(
    val type: Type,
    val value: Any?,
) {

    val properties = PropertiesDelegate()
    val methods = MethodsDelegate()

    operator fun get(property: Property): Property.Bound? {
        return properties[property.name]
    }

    operator fun get(method: Method): Method.Bound? {
        return methods[method.name, method.parameters.map { it.type }]
    }

    inner class PropertiesDelegate {

        operator fun get(name: String): Property.Bound? {
            return methods[name, listOf()]?.let { getter ->
                Property.Bound(getter, methods[name, listOf(getter.returns)])
            }
        }

    }

    inner class MethodsDelegate {

        operator fun get(name: String, arguments: List<Type>): Method.Bound? {
            return type.functions[name, listOf(type) + arguments]?.let {
                Method.Bound(it as Function.Definition, this@Object)
            }
        }

        /**
         * Invokes `to(String)` on the object. This is placed here as a bit of a
         * hack to avoid overriding the default data class toString, but also
         * helps make it clear this invokes a user method.
         */
        override fun toString(): String {
            val type = Type.TYPE[Type.STRING]
            return get("to", listOf(type))!!.invoke(listOf(Object(type, type))).value as String
        }

    }

}
