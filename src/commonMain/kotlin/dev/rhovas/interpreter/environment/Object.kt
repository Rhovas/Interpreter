package dev.rhovas.interpreter.environment

import com.ionspin.kotlin.bignum.integer.BigInteger

data class Object(
    val type: Type,
    val value: Any?,
) {

    init {
        require(type.component.scope is Scope.Definition)
    }

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
         * Invokes `==(other)` on the object if defined or returns false. This
         * is placed here to avoid overriding the default data class `equals`,
         * but also makes it clearer this invokes a user method.
         */
        fun equals(other: Object): Boolean {
            return get("==", listOf(other.type))?.invoke(listOf(other))?.value as Boolean? ?: false
        }

        /**
         * Invokes `hash` on the object. This is placed here to match `equals`/
         * `toString`, but also makes it clearer this invokes a user method.
         */
        fun hash(): Int {
            return (get("hash", listOf())!!.invoke(listOf()).value as BigInteger).intValue()
        }

        /**
         * Invokes `to(String)` on the object. This is placed here to avoid
         * overriding the default data class `toString`, but also makes it
         * clearer this invokes a user method.
         */
        override fun toString(): String {
            val type = Type.TYPE[Type.STRING]
            return get("to", listOf(type))!!.invoke(listOf(Object(type, type))).value as String
        }

    }

    data class Hashable(val instance: Object) {

        override fun equals(other: Any?) = instance.methods.equals((other as Hashable).instance)
        override fun hashCode() = instance.methods.hash()
        override fun toString() = instance.methods.toString()

    }

}
