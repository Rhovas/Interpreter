package dev.rhovas.interpreter.environment.type

import dev.rhovas.interpreter.RhovasSpec
import dev.rhovas.interpreter.environment.Component
import dev.rhovas.interpreter.environment.Function
import dev.rhovas.interpreter.environment.Modifiers
import dev.rhovas.interpreter.environment.Scope
import dev.rhovas.interpreter.environment.Variable
import dev.rhovas.interpreter.library.Library
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class SubtypeTests : RhovasSpec() {

    enum class Subtype { TRUE, FALSE, INVARIANT }

    private val SUPERTYPE = reference("Supertype", linkedMapOf(), listOf(Type.ANY))
    private val TYPE = reference("Type", linkedMapOf(), listOf(SUPERTYPE))
    private val SUBTYPE = reference("Subtype", linkedMapOf(), listOf(TYPE))
    private val STRUCT_SUBTYPE = reference("StructSubtype", linkedMapOf(), listOf(Type.STRUCT[struct("x" to TYPE)]))

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

    data class Test(
        val type: Type,
        val other: Type,
        val expected: Any, // Boolean | Map<String, Type>
    )

    private fun test(test: Test, wrapper: Type.Reference? = null) {
        fun test(type: Type, other: Type, expected: Any) {
            val bindings = mutableMapOf<String, Type>()
            val subtype = isSubtypeOf(type, other, bindings)
            when (expected) {
                is Boolean -> assertEquals(expected, subtype)
                is Map<*, *> -> assertEquals(expected, if (subtype) bindings else false)
                else -> throw AssertionError(expected::class)
            }
        }
        if (test.expected !is Subtype) {
            test(test.type, test.other, test.expected)
        } else {
            // Stub for existing tests
            test(test.type, test.other, test.expected != Subtype.FALSE)
            test(Type.LIST[test.type], Type.LIST[test.other], test.expected == Subtype.INVARIANT)
            wrapper?.let {
                val wrappedType = Type.Reference(it.component, mapOf(it.component.generics.keys.single() to test.type))
                val wrappedOther = Type.Reference(it.component, mapOf(it.component.generics.keys.single() to test.other))
                test(wrappedType, wrappedOther, test.expected != Subtype.FALSE)
                test(Type.LIST[wrappedType], Type.LIST[wrappedOther], test.expected == Subtype.INVARIANT)
            }
        }
    }

    init {
        suite("Reference <: Reference") {

            suite("Base", listOf(
                "Equal" to Test(TYPE, TYPE, true),
                "Disjoint" to Test(TYPE, Type.VOID, false),
                "Subtype" to Test(SUBTYPE, TYPE, true),
                "Supertype" to Test(SUPERTYPE, TYPE, false),
                "Grandchild" to Test(SUBTYPE, SUPERTYPE, true),
                "Grandparent" to Test(SUPERTYPE, SUBTYPE, false),
                "Any Subtype" to Test(Type.ANY, TYPE, false),
                "Any Supertype" to Test(TYPE, Type.ANY, true),
                "Dynamic Subtype" to Test(Type.DYNAMIC, TYPE, true),
                "Dynamic Supertype" to Test(TYPE, Type.DYNAMIC, true),
            )) { test(it) }

            suite("Generics", listOf(
                "Equal" to Test(Type.LIST[TYPE], Type.LIST[TYPE], true),
                "Base Subtype" to Test(Type.LIST[TYPE], Type.ITERABLE[TYPE], true),
                "Base Supertype" to Test(Type.ITERABLE[TYPE], Type.LIST[TYPE], false),
                "Generic Subtype" to Test(Type.LIST[TYPE], Type.LIST[TYPE], true),
                "Generic Supertype" to Test(Type.LIST[TYPE], Type.LIST[TYPE], true),
                "Generic Dynamic Subtype" to Test(Type.LIST[TYPE], Type.LIST[TYPE], true),
                "Generic Dynamic Supertype" to Test(Type.LIST[TYPE], Type.LIST[TYPE], true),
            )) { test(it) }

        }

        suite("Reference <: Tuple", listOf(
            "Equal" to Test(Type.TUPLE[tuple(TYPE)], tuple(TYPE), true),
            // Note: Unlike Struct, Tuple is a final class hence no subtypes.
            "Base Supertype" to Test(Type.ANY, tuple(TYPE), false),
            "Base Dynamic" to Test(Type.DYNAMIC, tuple(TYPE), true),
            "Field Subtype" to Test(Type.TUPLE[tuple(SUBTYPE)], tuple(TYPE), true),
            "Field Supertype" to Test(Type.TUPLE[tuple(SUPERTYPE)], tuple(TYPE), false),
            "Field Dynamic" to Test(Type.TUPLE.DYNAMIC, tuple(TYPE), true),
        )) { test(it) }

        suite("Reference <: Struct", listOf(
            "Equal" to Test(Type.STRUCT[struct("x" to TYPE)], struct("x" to TYPE), true),
            "Base Subtype" to Test(STRUCT_SUBTYPE, struct("x" to TYPE), true),
            "Base Supertype" to Test(Type.ANY, struct("x" to TYPE), false),
            "Base Dynamic" to Test(Type.DYNAMIC, struct("x" to TYPE), true),
            "Field Subtype" to Test(Type.STRUCT[struct("x" to SUBTYPE)], struct("x" to TYPE), true),
            "Field Supertype" to Test(Type.STRUCT[struct("x" to SUPERTYPE)], struct("x" to TYPE), false),
            "Field Dynamic" to Test(Type.STRUCT.DYNAMIC, struct("x" to TYPE), true),
        )) { test(it) }

        suite("Tuple <: Reference", listOf(
            "Equal" to Test(tuple(TYPE), Type.TUPLE[tuple(TYPE)], true),
            "Supertype" to Test(tuple(TYPE), Type.ANY, true),
            "Dynamic" to Test(tuple(TYPE), Type.DYNAMIC, true),
        )) { test(it) }

        suite("Tuple <: Tuple") {

            suite("Fields", listOf(
                "Empty" to Test(tuple(), tuple(), true),
                "Single" to Test(tuple(TYPE), tuple(TYPE), true),
                "Multiple" to Test(tuple(TYPE, TYPE, TYPE), tuple(TYPE, TYPE, TYPE), true),
                "Extra" to Test(tuple(TYPE, TYPE), tuple(TYPE), true),
                "Missing" to Test(tuple(), tuple(TYPE), false),
            )) { test(it) }

            suite("Types", listOf(
                "Subtype" to Test(tuple(SUBTYPE), tuple(TYPE), true),
                "Supertype" to Test(tuple(SUPERTYPE), tuple(TYPE), false),
                "Multiple Subtype" to Test(tuple(SUBTYPE, SUBTYPE), tuple(TYPE, TYPE), true),
                "Multiple Supertype First" to Test(tuple(SUPERTYPE, TYPE), tuple(TYPE, TYPE), false),
                "Multiple Supertype Second" to Test(tuple(TYPE, SUPERTYPE), tuple(TYPE, TYPE), false),
            )) { test(it) }

            suite("Mutability", listOf(
                "Mutable Field Equal" to Test(tuple(TYPE, mutable = true), tuple(TYPE, mutable = true), true),
                "Mutable Field Subtype" to Test(tuple(SUBTYPE, mutable = true), tuple(TYPE, mutable = true), false),
                "Mutable Field Supertype" to Test(tuple(SUPERTYPE, mutable = true), tuple(TYPE, mutable = true), false),
                "Mutable Subtype Field Equal" to Test(tuple(TYPE, mutable = true), tuple(TYPE), true),
                "Mutable Subtype Field Subtype" to Test(tuple(SUBTYPE, mutable = true), tuple(TYPE), true),
                "Mutable Subtype Field Supertype" to Test(tuple(SUPERTYPE, mutable = true), tuple(TYPE), false),
                "Mutable Supertype Field Equal" to Test(tuple(TYPE), tuple(TYPE, mutable = true), false),
                "Mutable Supertype Field Subtype" to Test(tuple(SUBTYPE), tuple(TYPE, mutable = true), false),
                "Mutable Supertype Field Supertype" to Test(tuple(SUPERTYPE), tuple(TYPE, mutable = true), false),
            )) { test(it) }

        }

        suite("Tuple <: Struct", listOf(
            "Struct" to Test(tuple(TYPE), Type.STRUCT[struct("0" to TYPE)], false),
        )) { test(it) }

        suite("Struct <: Struct") {

            suite("Fields", listOf(
                "Empty" to Test(struct(), struct(), true),
                "Single" to Test(struct("x" to TYPE), struct("x" to TYPE), true),
                "Multiple" to Test(struct("x" to TYPE, "y" to TYPE, "z" to TYPE), struct("x" to TYPE, "y" to TYPE, "z" to TYPE), true),
                "Extra" to Test(struct("x" to TYPE, "y" to TYPE), struct("x" to TYPE), true),
                "Missing" to Test(struct(), struct("x" to TYPE), false),
                "Different" to Test(struct("x" to TYPE, "y" to TYPE), struct("x" to TYPE, "z" to TYPE), false),
            )) { test(it) }

            suite("Types", listOf(
                "Subtype" to Test(struct("x" to SUBTYPE), struct("x" to TYPE), true),
                "Supertype" to Test(struct("x" to SUPERTYPE), struct("x" to TYPE), false),
                "Multiple Subtype" to Test(struct("x" to SUBTYPE, "y" to SUBTYPE), struct("x" to TYPE, "y" to TYPE), true),
                "Multiple Supertype First" to Test(struct("x" to SUPERTYPE, "y" to TYPE), struct("x" to TYPE, "y" to TYPE), false),
                "Multiple Supertype Second" to Test(struct("x" to TYPE, "y" to SUPERTYPE), struct("x" to TYPE, "y" to TYPE), false),
            )) { test(it) }

            suite("Mutability", listOf(
                "Mutable Field Equal" to Test(struct("x" to TYPE, mutable = true), struct("x" to TYPE, mutable = true), true),
                "Mutable Field Subtype" to Test(struct("x" to SUBTYPE, mutable = true), struct("x" to TYPE, mutable = true), false),
                "Mutable Field Supertype" to Test(struct("x" to SUPERTYPE, mutable = true), struct("x" to TYPE, mutable = true), false),
                "Mutable Subtype Field Equal" to Test(struct("x" to TYPE, mutable = true), struct("x" to TYPE), true),
                "Mutable Subtype Field Subtype" to Test(struct("x" to SUBTYPE, mutable = true), struct("x" to TYPE), true),
                "Mutable Subtype Field Supertype" to Test(struct("x" to SUPERTYPE, mutable = true), struct("x" to TYPE), false),
                "Mutable Supertype Field Equal" to Test(struct("x" to TYPE), struct("x" to TYPE, mutable = true), false),
                "Mutable Supertype Field Subtype" to Test(struct("x" to SUBTYPE), struct("x" to TYPE, mutable = true), false),
                "Mutable Supertype Field Supertype" to Test(struct("x" to SUPERTYPE), struct("x" to TYPE, mutable = true), false),
            )) { test(it) }

        }

        suite("Generic") {
            suite("Raw", listOf(
                "Equal" to Test(generic("T", Type.NUMBER), generic("T", Type.NUMBER), Subtype.INVARIANT),
                "Unequal" to Test(generic("T", Type.NUMBER), generic("R", Type.NUMBER), Subtype.FALSE),
                "Bound Subtype" to Test(generic("T", Type.INTEGER), Type.NUMBER, Subtype.TRUE),
                "Bound Supertype" to Test(generic("T", Type.NUMBER), Type.INTEGER, Subtype.FALSE),
                "Bound Dynamic Subtype" to Test(generic("T", Type.DYNAMIC), Type.NUMBER, Subtype.INVARIANT),
                "Bound Dynamic Supertype" to Test(generic("T", Type.NUMBER), Type.DYNAMIC, Subtype.INVARIANT),
            )) { test(it) }

            suite("Unbound", listOf(
                "Equal" to Test(Type.LIST.GENERIC, Type.LIST.GENERIC, Subtype.INVARIANT),
                "Subtype" to Test(Type.LIST.GENERIC, Type.ITERABLE.GENERIC, Subtype.TRUE),
                "Supertype" to Test(Type.ITERABLE.GENERIC, Type.LIST.GENERIC, Subtype.FALSE),
                "Grandchild" to Test(Type.LIST.GENERIC, Type.ANY, Subtype.TRUE),
                "Generic Subtype" to Test(Type.LIST[Type.INTEGER], Type.LIST.GENERIC, Subtype.INVARIANT),
                "Generic Supertype" to Test(Type.LIST.GENERIC, Type.LIST[Type.INTEGER], Subtype.FALSE),
                "Generic Dynamic Subtype" to Test(Type.LIST[Type.DYNAMIC], Type.LIST.GENERIC, Subtype.INVARIANT),
                "Generic Dynamic Supertype" to Test(Type.LIST.GENERIC, Type.LIST[Type.DYNAMIC], Subtype.INVARIANT),
            )) { test(it) }

            suite("Bound", listOf(
                "Equal" to Test(Type.LIST[Type.NUMBER], Type.LIST[Type.NUMBER], Subtype.INVARIANT),
                "Base Subtype" to Test(Type.LIST[Type.NUMBER], Type.ITERABLE[Type.NUMBER], Subtype.TRUE),
                "Base Supertype" to Test(Type.ITERABLE[Type.NUMBER], Type.LIST[Type.NUMBER], Subtype.FALSE),
                "Base Grandchild" to Test(Type.LIST[Type.NUMBER], Type.ANY, Subtype.TRUE),
                "Generic Subtype" to Test(Type.LIST[Type.INTEGER], Type.LIST[Type.NUMBER], Subtype.FALSE),
                "Generic Supertype" to Test(Type.LIST[Type.NUMBER], Type.LIST[Type.INTEGER], Subtype.FALSE),
                "Generic Dynamic Subtype" to Test(Type.LIST[Type.DYNAMIC], Type.LIST[Type.NUMBER], Subtype.INVARIANT),
                "Generic Dynamic Supertype" to Test(Type.LIST[Type.NUMBER], Type.LIST[Type.DYNAMIC], Subtype.INVARIANT),
            )) { test(it) }
        }

        suite("Variant") {
            suite("Covariant", listOf(
                "Subtype" to Test(Type.INTEGER, Type.Variant(null, Type.NUMBER), Subtype.INVARIANT),
                "Supertype" to Test(Type.ANY, Type.Variant(null, Type.NUMBER), Subtype.FALSE),
                "Inverse Subtype" to Test(Type.Variant(null, Type.NUMBER), Type.INTEGER, Subtype.FALSE),
                "Inverse Supertype" to Test(Type.Variant(null, Type.NUMBER), Type.ANY, Subtype.TRUE),
                "Variant Subtype" to Test(Type.Variant(null, Type.NUMBER), Type.Variant(null, Type.INTEGER), Subtype.FALSE),
                "Variant Supertype" to Test(Type.Variant(null, Type.NUMBER), Type.Variant(null, Type.ANY), Subtype.INVARIANT),
                "Reference" to Test(Type.Variant(null, Type.NUMBER), Type.ANY, Subtype.TRUE),
            )) { test(it) }

            suite("Contravariant", listOf(
                "Subtype" to Test(Type.INTEGER, Type.Variant(Type.NUMBER, null), Subtype.FALSE),
                "Supertype" to Test(Type.ANY, Type.Variant(Type.NUMBER, null), Subtype.INVARIANT),
                "Inverse Subtype" to Test(Type.Variant(Type.NUMBER, null), Type.INTEGER, Subtype.FALSE),
                "Inverse Supertype" to Test(Type.Variant(Type.NUMBER, null), Type.ANY, Subtype.TRUE),
                "Variant Subtype" to Test(Type.Variant(Type.NUMBER, null), Type.Variant(Type.INTEGER, null), Subtype.INVARIANT),
                "Variant Supertype" to Test(Type.Variant(Type.NUMBER, null), Type.Variant(Type.ANY, null), Subtype.FALSE),
                "Reference" to Test(Type.Variant(Type.NUMBER, null), Type.ANY, Subtype.TRUE),
            )) { test(it) }

            suite("Unbound", listOf(
                "Type" to Test(Type.NUMBER, Type.Variant(null, null), Subtype.INVARIANT),
                "Inverse Type" to Test(Type.Variant(null, null), Type.NUMBER, Subtype.FALSE),
                "Variant Subtype" to Test(Type.Variant(Type.INTEGER, Type.ANY), Type.Variant(null, null), Subtype.INVARIANT),
                "Variant Supertype" to Test(Type.Variant(null, null), Type.Variant(Type.INTEGER, Type.ANY), Subtype.FALSE),
                "Reference" to Test(Type.Variant(null, null), Type.ANY, Subtype.TRUE),
            )) { test(it) }
        }
    }

    private fun reference(name: String, generics: LinkedHashMap<String, Type.Generic>, inherits: List<Type.Reference>): Type.Reference {
        val component = Component.Class(name, modifiers = Modifiers(Modifiers.Inheritance.VIRTUAL))
        component.generics.putAll(generics)
        component.inherits.addAll(inherits)
        component.inherits.forEach { component.inherit(it) }
        return component.type
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

    private fun variant(lower: Type? = null, upper: Type? = null): Type.Variant {
        return Type.Variant(lower, upper)
    }

}
