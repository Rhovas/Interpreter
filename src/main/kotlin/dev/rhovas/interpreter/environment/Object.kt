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
        return methods[method.name, method.parameters.size]
    }

    inner class PropertiesDelegate {

        operator fun get(name: String): Variable.Property.Bound? {
            return methods[name, 0]?.let { getter ->
                Variable.Property.Bound(getter, methods[name, 1])
            }
        }

    }

    inner class MethodsDelegate {

        operator fun get(name: String, arity: Int): Function.Method.Bound? {
            return type.functions[name, 1 + arity]?.let { function ->
                Function.Method.Bound(this@Object, function as Function.Definition)
            }
        }

    }

}
