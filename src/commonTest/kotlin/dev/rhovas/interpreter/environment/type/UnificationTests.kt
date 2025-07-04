package dev.rhovas.interpreter.environment.type

import dev.rhovas.interpreter.RhovasSpec
import dev.rhovas.interpreter.environment.Component
import dev.rhovas.interpreter.environment.Modifiers
import dev.rhovas.interpreter.library.Library
import kotlin.test.assertEquals

class UnificationTests : RhovasSpec() {

    //Not ideal, but based on the old tests and can't be trivially updated.
    private val Type.Companion.NUMBER by lazy {
        val component = Component.Class("Number", Modifiers(Modifiers.Inheritance.ABSTRACT))
        component.inherits.add(Type.COMPARABLE[component.type])
        component.inherits.forEach { component.inherit(it) }
        Type.INTEGER.component.inherits.add(0, component.type)
        Type.DECIMAL.component.inherits.add(0, component.type)
        //Library.SCOPE.types.define(component.name, component.type)
        component.type
    }

    init {
        data class Test(val type: Type, val other: Type, val expected: Type)

        fun test(type: Type, other: Type, expected: Type, wrapper: Component<*>) {
            assertEquals(expected, type.unify(other))
            assertEquals(expected, other.unify(type))
            val wrappedType = Type.Reference(wrapper, mapOf(wrapper.generics.keys.single() to type))
            val wrappedOther = Type.Reference(wrapper, mapOf(wrapper.generics.keys.single() to other))
            val wrappedExpected = Type.Reference(wrapper, mapOf(wrapper.generics.keys.single() to expected))
            assertEquals(wrappedExpected, wrappedType.unify(wrappedOther))
            assertEquals(wrappedExpected, wrappedOther.unify(wrappedType))
        }

        suite("Reference", listOf(
            "Equal" to Test(Type.INTEGER, Type.INTEGER, Type.INTEGER),
            "Disjoint" to Test(Type.INTEGER, Type.REGEX, Type.ANY),
            "Supertype" to Test(Type.INTEGER, Type.NUMBER, Type.NUMBER),
            "Common Supertype" to Test(Type.INTEGER, Type.DECIMAL, Type.NUMBER),
            "Dynamic" to Test(Type.INTEGER, Type.DYNAMIC, Type.DYNAMIC),
            "Dynamic Any" to Test(Type.DYNAMIC, Type.ANY, Type.DYNAMIC),
        )) { test(it.type, it.other, it.expected, Type.LIST.GENERIC.component) }

        suite("Tuple", listOf(
            "Equal" to Test(tuple(Type.INTEGER), tuple(Type.INTEGER), tuple(Type.INTEGER)),
            "Disjoint" to Test(tuple(Type.INTEGER), tuple(Type.REGEX), tuple(Type.ANY)),
            "Different Size" to Test(tuple(Type.INTEGER, Type.INTEGER), tuple(Type.INTEGER), tuple(Type.INTEGER)),
            "Any" to Test(tuple(Type.INTEGER), Type.ANY, Type.ANY),
            "Dynamic" to Test(tuple(Type.INTEGER), Type.DYNAMIC, Type.DYNAMIC),
        )) { test(it.type, it.other, it.expected, Type.TUPLE.GENERIC.component) }

        suite("Struct", listOf(
            "Equal" to Test(struct("x" to Type.INTEGER), struct("x" to Type.INTEGER), struct("x" to Type.INTEGER)),
            "Disjoint" to Test(struct("x" to Type.INTEGER), struct("x" to Type.REGEX), struct("x" to Type.ANY)),
            "Different Size" to Test(struct("x" to Type.INTEGER, "y" to Type.INTEGER), struct("x" to Type.INTEGER), struct("x" to Type.INTEGER)),
            "Any" to Test(struct("x" to Type.INTEGER), Type.ANY, Type.ANY),
            "Dynamic" to Test(struct("x" to Type.INTEGER), Type.DYNAMIC, Type.DYNAMIC),
        )) { test(it.type, it.other, it.expected, Type.TUPLE.GENERIC.component) }

        suite("Generic", listOf(
            "Equal" to Test(generic("T"), generic("T"), generic("T")),
            "Unbound" to Test(generic("T"), Type.INTEGER, Type.ANY),
        )) { test(it.type, it.other, it.expected, Type.LIST.GENERIC.component) }

        suite("Variant", listOf(
            "Equal" to Test(Type.Variant(Type.INTEGER, Type.NUMBER), Type.Variant(Type.INTEGER, Type.NUMBER), Type.Variant(Type.INTEGER, Type.NUMBER)),
            "Unbound" to Test(Type.Variant(null, null), Type.INTEGER, Type.ANY),
        )) { test(it.type, it.other, it.expected, Type.LIST.GENERIC.component) }
    }

    private fun tuple(vararg types: Type, mutable: Boolean = false): Type.Tuple {
        return (Type.TUPLE[types.toList(), mutable].generics["T"]!! as Type.Tuple)
    }

    private fun struct(vararg entries: Pair<String, Type>, mutable: Boolean = false): Type.Struct {
        return Type.STRUCT[entries.toList(), mutable].generics["T"]!! as Type.Struct
    }

    private fun generic(name: String, bound: Type = Type.ANY): Type.Generic {
        return Type.Generic(name, bound)
    }

}
