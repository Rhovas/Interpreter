package dev.rhovas.interpreter.environment

data class Object(
    val type: Type,
    val value: Any?,
) {

    val properties = PropertiesDelegate()
    val methods = MethodsDelegate()

    operator fun get(property: Variable.Property): Variable.Property.Bound? {
        return properties[property.name]
    }

    operator fun get(method: Function.Method): Function.Method.Bound? {
        return methods[method.name, method.parameters.map { it.second }]
    }

    inner class PropertiesDelegate {

        operator fun get(name: String): Variable.Property.Bound? {
            return methods[name, listOf()]?.let { getter ->
                Variable.Property.Bound(getter, methods[name, listOf(getter.returns)])
            }
        }

    }

    inner class MethodsDelegate {

        operator fun get(name: String, arguments: List<Type>): Function.Method.Bound? {
            return type.functions[name, listOf(type) + arguments]?.let {
                Function.Method.Bound(this@Object, it)
            }
        }

    }

}
