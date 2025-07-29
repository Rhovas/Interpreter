package dev.rhovas.interpreter.environment.type

import dev.rhovas.interpreter.environment.Variable

fun unify(type: Type, other: Type): Type = when(type) {
    is Type.Reference -> unify(type, other)
    is Type.Tuple -> unify(type, other)
    is Type.Struct -> unify(type, other)
    is Type.Generic -> unify(type, other)
    is Type.Variant -> unify(type, other)
}

private fun unify(type: Type.Reference, other: Type): Type = when (other) {
    is Type.Reference -> when {
        type.component.name == "Dynamic" || other.component.name == "Dynamic" -> Type.DYNAMIC
        type.component.name == "Any" || other.component.name == "Any" -> Type.ANY
        type.component.name == other.component.name -> Type.Reference(type.component, type.generics.keys.associateWith { unify(type.generics[it]!!, other.generics[it]!!) })
        else -> {
            var top: Type.Reference = other
            while (!isSubtypeOf(type, top, Bindings.None)) {
                top = top.component.inherits.first().bind(type.generics)
            }
            unify(top, type)
        }
    }
    is Type.Tuple -> unify(type, Type.TUPLE[other])
    is Type.Struct -> unify(type, Type.STRUCT[other])
    is Type.Generic -> unify(type, other.bound)
    is Type.Variant -> unify(type, other.upper ?: Type.ANY)
}

private fun unify(type: Type.Tuple, other: Type): Type = when (other) {
    is Type.Tuple -> Type.Tuple(type.elements.zip(other.elements).mapIndexed { index, pair ->
        Variable.Declaration(index.toString(), unify(pair.first.type, pair.second.type), pair.first.mutable && pair.second.mutable)
    })
    else -> unify(Type.TUPLE[type], other)
}

private fun unify(type: Type.Struct, other: Type): Type = when (other) {
    is Type.Struct -> Type.Struct(type.fields.keys.intersect(other.fields.keys).associateWith {
        Variable.Declaration(it, unify(type.fields[it]!!.type, other.fields[it]!!.type), type.fields[it]!!.mutable && other.fields[it]!!.mutable,)
    })
    else -> unify(Type.STRUCT[type], other)
}

private fun unify(type: Type.Generic, other: Type): Type = when {
    other is Type.Generic -> if (type.name == other.name) type else unify(type.bound, other.bound)
    else -> unify(type.bound, other)
}

private fun unify(type: Type.Variant, other: Type): Type = when (other) {
    is Type.Variant -> Type.Variant(
        if (type.lower != null && other.lower != null) unify(type.lower, other.lower) else null,
        unify(type.upper ?: Type.ANY, other.upper ?: Type.ANY),
    )
    else -> unify(type.upper ?: Type.ANY, other)
}
