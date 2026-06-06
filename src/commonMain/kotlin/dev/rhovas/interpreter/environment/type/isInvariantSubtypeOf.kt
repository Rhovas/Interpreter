package dev.rhovas.interpreter.environment.type

import dev.rhovas.interpreter.environment.Variable

fun isInvariantSubtypeOf(type: Type, other: Type, bindings: Bindings): Boolean = when(type) {
    is Type.Dynamic -> when (other) {
        is Type.Dynamic -> isInvariantSubtypeOf(type, other, bindings)
        is Type.Reference -> isInvariantSubtypeOf(type, other, bindings)
        is Type.Tuple -> isInvariantSubtypeOf(type, other, bindings)
        is Type.Struct -> isInvariantSubtypeOf(type, other, bindings)
        is Type.Variant -> isInvariantSubtypeOf(type, other, bindings)
        is Type.Generic -> isInvariantSubtypeOf(type, other, bindings)
    }
    is Type.Reference -> when (other) {
        is Type.Dynamic -> isInvariantSubtypeOf(type, other, bindings)
        is Type.Reference -> isInvariantSubtypeOf(type, other, bindings)
        is Type.Tuple -> isInvariantSubtypeOf(type, Type.TUPLE[other], bindings)
        is Type.Struct -> isInvariantSubtypeOf(type, Type.STRUCT[other], bindings)
        is Type.Variant -> isInvariantSubtypeOf(type, other, bindings)
        is Type.Generic -> isInvariantSubtypeOf(type, other, bindings)
    }
    is Type.Tuple -> when (other) {
        is Type.Tuple -> isInvariantSubtypeOf(type, other, bindings)
        else -> isInvariantSubtypeOf(Type.TUPLE[type], other, bindings)
    }
    is Type.Struct -> when (other) {
        is Type.Struct -> isInvariantSubtypeOf(type, other, bindings)
        else -> isInvariantSubtypeOf(Type.STRUCT[type], other, bindings)
    }
    is Type.Generic -> when (other) {
        is Type.Dynamic -> isInvariantSubtypeOf(type, other, bindings)
        is Type.Reference -> isInvariantSubtypeOf(type, other, bindings)
        is Type.Tuple -> isInvariantSubtypeOf(type, Type.TUPLE[other], bindings)
        is Type.Struct -> isInvariantSubtypeOf(type, Type.STRUCT[other], bindings)
        is Type.Variant -> isInvariantSubtypeOf(type, other, bindings)
        is Type.Generic -> isInvariantSubtypeOf(type, other, bindings)
    }
    is Type.Variant -> when (other) {
        is Type.Dynamic -> isInvariantSubtypeOf(type, other, bindings)
        is Type.Reference -> isInvariantSubtypeOf(type, other, bindings)
        is Type.Tuple -> isInvariantSubtypeOf(type, Type.TUPLE[other], bindings)
        is Type.Struct -> isInvariantSubtypeOf(type, Type.STRUCT[other], bindings)
        is Type.Variant -> isInvariantSubtypeOf(type, other, bindings)
        is Type.Generic -> isInvariantSubtypeOf(type, other, bindings)
    }
}

private fun isInvariantSubtypeOf(type: Type.Dynamic, other: Type.Dynamic, bindings: Bindings): Boolean {
    return true
}

private fun isInvariantSubtypeOf(type: Type.Dynamic, other: Type.Reference, bindings: Bindings): Boolean {
    return other.generics.all { isInvariantSubtypeOf(type, it.value, bindings) }
}

private fun isInvariantSubtypeOf(type: Type.Dynamic, other: Type.Tuple, bindings: Bindings): Boolean {
    return other.elements.all { isInvariantSubtypeOf(type, it.type, bindings) }
}

private fun isInvariantSubtypeOf(type: Type.Dynamic, other: Type.Struct, bindings: Bindings): Boolean {
    return other.fields.all { isInvariantSubtypeOf(type, it.value.type, bindings) }
}

private fun isInvariantSubtypeOf(type: Type.Dynamic, other: Type.Generic, bindings: Bindings): Boolean {
    when {
        // If other is not bindable, then trivially true
        bindings.other == null -> Unit
        // If other is currently unbound, then invariant bind to Dynamic
        other.name !in bindings.other!! -> bindings.other!![other.name] = Type.DYNAMIC
        // If other is bound to a variant, overwrite with an invariant binding to propagate Dynamic during refinement
        //   e.g. for cons<T>(T, List<T>): List<T>, checking cons(Type, List<Dynamic>) binds T = Type : *
        //   Refining T = Type : * is not permitted by refinement; T must refine to a concrete type
        //   Refining T = Type is unsafe, e.g. cons(Type, List<Supertype>): List<Type> (!)
        //   Refining T = Any (* upper) is safe, but not ergonomic and defeats the point of Dynamic
        //   Refining T = Dynamic is thus ideal, hence binding it here
        // May be overwritten later with a non-Dynamic invariant binding, e.g. append2(Type, List<Dynamic>, List<Type>)
        bindings.other!![other.name] is Type.Variant -> bindings.other!![other.name] = Type.DYNAMIC
        // Otherwise, the current binding is already invariant and the correct type
        else -> Unit
    }
    return true
}

private fun isInvariantSubtypeOf(type: Type.Dynamic, other: Type.Variant, bindings: Bindings): Boolean {
    return isSubtypeOf(type, other, bindings)
}

private fun isInvariantSubtypeOf(type: Type.Reference, other: Type.Dynamic, bindings: Bindings): Boolean {
    return type.component.generics.all { isInvariantSubtypeOf(it.value, other, bindings) }
}

private fun isInvariantSubtypeOf(type: Type.Reference, other: Type.Reference, bindings: Bindings): Boolean {
    return when {
        type.component.name == other.component.name -> {
            type.generics.values.zip(other.generics.values).all { (type, other) ->
                isInvariantSubtypeOf(type, other, bindings)
            }
        }
        else -> false // no subtyping via inheritance for invariants
    }
}

private fun isInvariantSubtypeOf(type: Type.Reference, other: Type.Generic, bindings: Bindings): Boolean {
    if (bindings.other == null) {
        return false
    } else if (other.name !in bindings.other!!) {
        bindings.other!![other.name] = type // update binding first for recursive generic bounds
        return isSubtypeOf(type, other.bound, bindings)
    } else {
        val binding = bindings.other!![other.name]!!
        return isInvariantSubtypeOf(type, binding, Bindings.None).also {
            if (it) bindings.other!![other.name] = type // update binding only if subtype for debugging bindings
        }
    }
}

private fun isInvariantSubtypeOf(type: Type.Reference, other: Type.Variant, bindings: Bindings): Boolean {
    return isSubtypeOf(type, other, bindings)
}

private fun isInvariantSubtypeOf(type: Type.Tuple, other: Type.Tuple, bindings: Bindings): Boolean {
    return type.elements.size == other.elements.size
        && type.elements.zip(other.elements).all { (field, other) ->
            isInvariantSubtypeOf(field, other, bindings)
        }
}

private fun isInvariantSubtypeOf(type: Type.Struct, other: Type.Struct, bindings: Bindings): Boolean {
    return type.fields.keys == other.fields.keys
        && type.fields.map { it.value to other.fields[it.key]!! }.all { (field, other) ->
            isInvariantSubtypeOf(field, other, bindings)
        }
}

private fun isInvariantSubtypeOf(type: Type.Generic, other: Type.Dynamic, bindings: Bindings): Boolean {
    // Note: This case is only interesting when type is bindable (Bindings.Subtype). It's not clear if/when this is
    // reachable outside constructed unit tests to understand expected behavior.
    // Assume that List<T> <: List<Dynamic> should mirror List<Dynamic> <: List<T> and delegate.
    return isInvariantSubtypeOf(other, type, when (bindings) {
        is Bindings.None -> bindings
        is Bindings.Subtype -> Bindings.Supertype(bindings.type)
        is Bindings.Supertype -> Bindings.Subtype(bindings.other)
    })
}

private fun isInvariantSubtypeOf(type: Type.Generic, other: Type.Reference, bindings: Bindings): Boolean {
    if (bindings.type == null) {
        return false
    } else if (type.name !in bindings.type!!) {
        bindings.type!![type.name] = other // update binding first for recursive generic bounds
        return isSupertypeOf(type.bound, other, bindings)
    } else {
        val binding = bindings.type!![type.name]!!
        return when {
            // If binding is a concrete type, variant refinement is unnecessary
            binding !is Type.Variant -> isInvariantSubtypeOf(binding, other, Bindings.None)
            // Unlike isSubtypeOf, is*Invariant*SubtypeOf always requires refining the variant binding
            // Else, ensure variant binding constrains other and refine with an invariant binding
            //   e.g. List<T = Subtype : Supertype> <: List<Type> -> T = Type
            isSupertypeOf(binding, other, Bindings.None) -> true.also { bindings.type!![type.name] = other }
            else -> false
        }
    }
}

private fun isInvariantSubtypeOf(type: Type.Generic, other: Type.Generic, bindings: Bindings): Boolean {
    return when (bindings) {
        is Bindings.None -> type.name == other.name
        is Bindings.Subtype -> {
            val binding = bindings.type[type.name]
            if (binding is Type.Variant) {
                if (!isSupertypeOf(binding.upper ?: Type.ANY, other, Bindings.None)) {
                    return false
                } else if (binding.lower != null && !isSubtypeOf(binding.lower, other, Bindings.None)) {
                    return false
                }
                bindings.type[type.name] = other
                return true
            } else if (binding != null) {
                isInvariantSubtypeOf(binding, other, Bindings.None)
            } else {
                //TODO: Fix recursive generics checks loosing generic relations
                //See comment for Supertype bindings below
                bindings.type[type.name] = Type.DYNAMIC
                val result = isSupertypeOf(type.bound, other.bound, bindings)
                if (result) {
                    bindings.type[type.name] = other
                }
                return result
            }
        }
        is Bindings.Supertype -> {
            val binding = bindings.other[other.name]
            if (binding is Type.Variant) {
                if (binding.lower != null && isSubtypeOf(type, binding.lower, Bindings.None)) {
                    bindings.other[other.name] = type
                    return true
                } else if (!isSubtypeOf(type, binding.upper ?: Type.ANY, Bindings.None)) {
                    return false
                }
                bindings.other[other.name] = type
                return true
            } else if (binding != null) {
                isInvariantSubtypeOf(type, binding, Bindings.None)
            } else {
                //TODO: Fix recursive generics checks loosing generic relations
                //when lowering to bounds, e.g. T: String <: Equatable<T>. Is
                //this even well-formed? e.g. class MyNum : Equatable<Number>
                bindings.other[other.name] = Type.DYNAMIC //type.bound
                val result = isSubtypeOf(type.bound, other.bound, bindings)
                if (result) {
                    bindings.other[other.name] = type
                }
                return result
            }
        }
        else -> {
            //TODO: Stub
            val binding = bindings.other?.get(other.name)
            return when {
                binding == null -> type.name == other.name
                else -> isInvariantSubtypeOf(type, binding, bindings.also { it.other?.set(other.name, type) })
            }
        }
    }
}

private fun isInvariantSubtypeOf(type: Type.Generic, other: Type.Variant, bindings: Bindings): Boolean {
    return isSubtypeOf(type, other, bindings)
}

private fun isInvariantSubtypeOf(type: Type.Variant, other: Type.Dynamic, bindings: Bindings): Boolean {
    return isSubtypeOf(type, other, bindings)
}

private fun isInvariantSubtypeOf(type: Type.Variant, other: Type.Reference, bindings: Bindings): Boolean {
    return false
}

private fun isInvariantSubtypeOf(type: Type.Variant, other: Type.Generic, bindings: Bindings): Boolean {
    if (bindings.other == null) {
        return false
    } else if (bindings.other!!.containsKey(other.name)) {
        val binding = bindings.other!![other.name]!!
        if (binding is Type.Variant) {
            if (!isSubtypeOf(type.upper ?: Type.ANY, binding.upper ?: Type.ANY, Bindings.None)) {
                return false
            }
            if (binding.lower != null && (type.lower == null || !isSupertypeOf(type.lower, binding.lower, Bindings.None))) {
                return false
            }
            bindings.other!![other.name] = Type.Variant(type, binding)
            return true
        } else {
            return isInvariantSubtypeOf(type, binding, Bindings.None)
        }
    } else {
        //TODO: Audit contextualized use cases
        bindings.other!![other.name] = type
        return isSubtypeOf(type.upper ?: Type.ANY, other.bound, bindings)
    }
}

private fun isInvariantSubtypeOf(type: Type.Variant, other: Type.Variant, bindings: Bindings): Boolean {
    return isSubtypeOf(type, other, bindings)
}

private fun isInvariantSubtypeOf(field: Variable.Declaration, other: Variable.Declaration, bindings: Bindings): Boolean {
    return field.mutable == other.mutable && isInvariantSubtypeOf(field.type, other.type, bindings)
}
