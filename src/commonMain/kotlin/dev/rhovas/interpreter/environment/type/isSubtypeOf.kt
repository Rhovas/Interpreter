package dev.rhovas.interpreter.environment.type

fun isSubtypeOf(type: Type, other: Type, bindings: MutableMap<String, Type>): Boolean = when(type) {
    is Type.Reference -> when (other) {
        is Type.Reference -> isSubtypeOf(type, other, bindings)
        is Type.Tuple -> isSubtypeOf(type, Type.TUPLE[other], bindings)
        is Type.Struct -> isSubtypeOf(type, Type.STRUCT[other], bindings)
        is Type.Variant -> isSubtypeOf(type, other, bindings)
        is Type.Generic -> isSubtypeOf(type, other, bindings)
    }
    is Type.Tuple -> when (other) {
        is Type.Tuple -> isSubtypeOf(type, other, bindings)
        else -> isSubtypeOf(Type.TUPLE[type], other, bindings)
    }
    is Type.Struct -> when (other) {
        is Type.Struct -> isSubtypeOf(type, other, bindings)
        else -> isSubtypeOf(Type.STRUCT[type], other, bindings)
    }
    is Type.Generic -> when (other) {
        is Type.Reference -> isSubtypeOf(type, other, bindings)
        is Type.Tuple -> isSubtypeOf(type, Type.TUPLE[other], bindings)
        is Type.Struct -> isSubtypeOf(type, Type.STRUCT[other], bindings)
        is Type.Variant -> isSubtypeOf(type, other, bindings)
        is Type.Generic -> isSubtypeOf(type, other, bindings)
    }
    is Type.Variant -> when (other) {
        is Type.Reference -> isSubtypeOf(type, other, bindings)
        is Type.Tuple -> isSubtypeOf(type, Type.TUPLE[other], bindings)
        is Type.Struct -> isSubtypeOf(type, Type.STRUCT[other], bindings)
        is Type.Variant -> isSubtypeOf(type, other, bindings)
        is Type.Generic -> isSubtypeOf(type, other, bindings)
    }
}

private fun isSubtypeOf(type: Type.Reference, other: Type.Reference, bindings: MutableMap<String, Type>): Boolean {
    return when {
        // TODO: Dynamic should ensure generics are still bound.
        type.component.name == "Dynamic" || other.component.name == "Dynamic" -> true
        type.component.name == other.component.name -> when {
            type.component.name in listOf("Tuple", "Struct") -> {
                // TODO: Fix Tuple<Dynamic>/Struct<Dynamic> behavior.
                isSubtypeOf(type.generics.values.single(), other.generics.values.single(), bindings)
            }
            else -> {
                type.generics.values.zip(other.generics.values).all { (type, other) ->
                    isInvariantSubtypeOf(type, other, bindings)
                }
            }
        }
        else -> {
            type.component.inherits.any { inherited ->
                // TODO: Review accuracy of inherited.bind(type.generics).
                // TODO: Review binding behavior for false isSubtypeOf calls.
                isSubtypeOf(inherited.bind(type.generics), other, bindings)
            }
        }
    }
}

private fun isSubtypeOf(type: Type.Reference, other: Type.Generic, bindings: MutableMap<String, Type>): Boolean {
    return when {
        bindings.containsKey(other.name) -> isSubtypeOfBinding(type, other.name, bindings)
        type.component.name == "Dynamic" -> true.also { bindings[other.name] = Type.DYNAMIC }
        else -> isSubtypeOf(type, other.bound, bindings.also { it[other.name] = Type.Variant(type, null) })
    }
}

private fun isSubtypeOf(type: Type.Reference, other: Type.Variant, bindings: MutableMap<String, Type>): Boolean {
    return (
        isSubtypeOf(other.lower ?: Type.DYNAMIC, type, bindings) &&
        isSubtypeOf(type, other.upper ?: Type.DYNAMIC, bindings)
    )
}

private fun isSubtypeOf(type: Type.Tuple, other: Type.Tuple, bindings: MutableMap<String, Type>): Boolean {
    return other.elements.withIndex().all { (index, other) ->
        type.elements.getOrNull(index)?.let {
            val typeSubtype = isSubtypeOf(it.type, other.type, bindings)
            val mutableSubtype = !other.mutable || it.mutable && it.type.isSupertypeOf(other.type, bindings)
            typeSubtype && mutableSubtype
        } ?: false
    }
}

private fun isSubtypeOf(type: Type.Struct, other: Type.Struct, bindings: MutableMap<String, Type>): Boolean {
    return other.fields.all { (key, other) ->
        type.fields[key]?.let {
            val typeSubtype = isSubtypeOf(it.type, other.type, bindings)
            val mutableSubtype = !other.mutable || it.mutable && it.type.isSupertypeOf(other.type, bindings)
            typeSubtype && mutableSubtype
        } ?: false
    }
}

private fun isSubtypeOf(type: Type.Generic, other: Type.Reference, bindings: MutableMap<String, Type>): Boolean {
    return isSubtypeOf(type.bound, other, bindings)
}

private fun isSubtypeOf(type: Type.Generic, other: Type.Generic, bindings: MutableMap<String, Type>): Boolean {
    return when {
        bindings.containsKey(other.name) -> isSubtypeOfBinding(type, other.name, bindings)
        else -> type.name == other.name
    }
}

private fun isSubtypeOf(type: Type.Generic, other: Type.Variant, bindings: MutableMap<String, Type>): Boolean {
    return isSubtypeOf(type.bound, other, bindings)
}

private fun isSubtypeOf(type: Type.Variant, other: Type.Reference, bindings: MutableMap<String, Type>): Boolean {
    return isSubtypeOf(type.upper ?: Type.ANY, other, bindings)
}

private fun isSubtypeOf(type: Type.Variant, other: Type.Generic, bindings: MutableMap<String, Type>): Boolean {
    return isSubtypeOf(type.upper ?: Type.ANY, other, bindings)
}

private fun isSubtypeOf(type: Type.Variant, other: Type.Variant, bindings: MutableMap<String, Type>): Boolean {
    return (
        type.lower?.isSupertypeOf(other.lower ?: Type.DYNAMIC, bindings) ?: (other.lower == null) &&
        isSubtypeOf(type.upper ?: Type.ANY, other.upper ?: Type.DYNAMIC, bindings)
    )
}

private fun isSubtypeOfBinding(type: Type, name: String, bindings: MutableMap<String, Type>): Boolean {
    val subtype = isSubtypeOf(type, bindings[name]!!, bindings)
    if (subtype && bindings[name] is Type.Variant) {
        bindings[name] = Type.Variant(type, (bindings[name]!! as Type.Variant).upper)
    }
    return subtype
}
