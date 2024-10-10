package dev.rhovas.interpreter.environment.type

import dev.rhovas.interpreter.environment.Variable

fun unify(type: Type, other: Type, bindings: MutableMap<String, Type>): Type = when(type) {
    is Type.Reference -> unify(type, other, bindings)
    is Type.Tuple -> unify(type, other, bindings)
    is Type.Struct -> unify(type, other, bindings)
    is Type.Generic -> unify(type, other, bindings)
    is Type.Variant -> unify(type, other, bindings)
}

private fun unify(type: Type.Reference, other: Type, bindings: MutableMap<String, Type>): Type = when (other) {
    is Type.Reference -> when {
        type.component.name == "Dynamic" || other.component.name == "Dynamic" -> Type.DYNAMIC
        type.component.name == "Any" || other.component.name == "Any" -> Type.ANY
        type.component.name == other.component.name -> Type.Reference(type.component, type.generics.keys.associateWith { unify(type.generics[it]!!, other.generics[it]!!, bindings) })
        else -> {
            var top: Type.Reference = other
            while (!isSubtypeOf(type, top, bindings)) {
                top = top.component.inherits.first().bind(type.generics)
            }
            unify(top, type, bindings)
        }
    }
    is Type.Tuple -> unify(type, Type.TUPLE[other], bindings)
    is Type.Struct -> unify(type, Type.STRUCT[other], bindings)
    is Type.Generic -> when {
        bindings.containsKey(other.name) -> unify(type, bindings[other.name]!!, bindings)
        type.component.name == "Dynamic" -> Type.DYNAMIC.also { bindings[other.name] = Type.DYNAMIC }
        else -> unify(type, other.bound, bindings).also { bindings[other.name] = it }
    }
    is Type.Variant -> unify(type, other.upper ?: Type.ANY, bindings)
}

private fun unify(type: Type.Tuple, other: Type, bindings: MutableMap<String, Type>): Type = when (other) {
    is Type.Tuple -> Type.Tuple(type.elements.zip(other.elements).mapIndexed { index, pair ->
        Variable.Declaration(index.toString(), unify(pair.first.type, pair.second.type, bindings), pair.first.mutable && pair.second.mutable)
    })
    else -> unify(Type.TUPLE[type], other, bindings)
}

private fun unify(type: Type.Struct, other: Type, bindings: MutableMap<String, Type>): Type = when (other) {
    is Type.Struct -> Type.Struct(type.fields.keys.intersect(other.fields.keys).associateWith {
        Variable.Declaration(it, unify(type.fields[it]!!.type, other.fields[it]!!.type, bindings), type.fields[it]!!.mutable && other.fields[it]!!.mutable,)
    })
    else -> unify(Type.STRUCT[type], other, bindings)
}

private fun unify(type: Type.Generic, other: Type, bindings: MutableMap<String, Type>): Type = when {
    type === other -> type //short-circuit for recursive generics
    bindings.containsKey(type.name) -> unify(bindings[type.name]!!, other, bindings)
    other is Type.Generic -> if (type.name == other.name) type else unify(type.bound, other.bound, bindings)
    else -> unify(type.bound, other, bindings).also { bindings[type.name] = other }
}

private fun unify(type: Type.Variant, other: Type, bindings: MutableMap<String, Type>): Type = when (other) {
    is Type.Variant -> Type.Variant(
        if (type.lower != null && other.lower != null) unify(type.lower, other.lower, bindings) else null,
        unify(type.upper ?: Type.ANY, other.upper ?: Type.ANY, bindings),
    )
    else -> unify(type.upper ?: Type.ANY, other, bindings)
}
