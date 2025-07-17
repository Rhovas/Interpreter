package dev.rhovas.interpreter.environment.type

import dev.rhovas.interpreter.RhovasSpec
import dev.rhovas.interpreter.environment.Component
import dev.rhovas.interpreter.environment.Modifiers
import kotlin.test.assertEquals

class SubtypeTests : RhovasSpec() {

    enum class Subtype { TRUE, FALSE, INVARIANT }

    private val SUPERTYPE = reference("Supertype", linkedMapOf(), listOf(Type.ANY))
    private val TYPE = reference("Type", linkedMapOf(), listOf(SUPERTYPE))
    private val SUBTYPE = reference("Subtype", linkedMapOf(), listOf(TYPE))
    private val STRUCT_SUBTYPE = reference("StructSubtype", linkedMapOf(), listOf(Type.STRUCT[struct("x" to TYPE)]))

    private val T = generic("T", TYPE)
    private val T_SUBTYPE = generic("T", SUBTYPE)
    private val T_SUPERTYPE = generic("T", SUPERTYPE)
    private val T_ANY = generic("T", Type.ANY)
    private val R = generic("R", Type.ANY)

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
        val bindings: Map<String, Type>?,
        val expected: Any, // Boolean | Map<String, Type>
        val invariant: Any = false, // Boolean | Map<String, Type>
    ) {
        constructor(type: Type, other: Type, expected: Subtype) : this(type, other, null, expected, expected == Subtype.INVARIANT)
        constructor(type: Type, other: Type, expected: Boolean, invariant: Boolean = false) : this(type, other, null, expected, invariant)
        //constructor(type: Type, other: Type, bindings: Map<String, Type>, expected: Any, invariant: Any = false) : this(type, other, bindings, expected, invariant)
    }

    private fun test(test: Test, wrapper: Type.Reference? = null) {
        fun test(type: Type, other: Type, initialBindings: Map<String, Type>?, expected: Any) {
            if (initialBindings == null) {
                val subtype = isSubtypeOf(type, other, Bindings.None)
                assertEquals(expected, subtype, "isSubtypeOf(${type}, ${other}):")
            } else {
                val finalBindings = initialBindings.toMutableMap()
                val subtype = isSubtypeOf(type, other, Bindings.Supertype(finalBindings))
                assertEquals(
                    Pair(expected != false, expected as? Map<*, *> ?: finalBindings),
                    Pair(subtype, finalBindings),
                    "isSubtypeOf(${type}, ${other}, ${initialBindings}):"
                )
            }
        }
        if (test.expected !is Subtype) {
            test(test.type, test.other, test.bindings, test.expected)
            test(Type.LIST[test.type], Type.LIST[test.other], test.bindings, test.invariant)
        } else {
            // Stub for existing tests
            test(test.type, test.other, mapOf(), test.expected != Subtype.FALSE)
            test(Type.LIST[test.type], Type.LIST[test.other], mapOf(), test.expected == Subtype.INVARIANT)
            wrapper?.let {
                val wrappedType = Type.Reference(it.component, mapOf(it.component.generics.keys.single() to test.type))
                val wrappedOther = Type.Reference(it.component, mapOf(it.component.generics.keys.single() to test.other))
                test(wrappedType, wrappedOther, mapOf(), test.expected != Subtype.FALSE)
                test(Type.LIST[wrappedType], Type.LIST[wrappedOther], mapOf(), test.expected == Subtype.INVARIANT)
            }
        }
    }

    init {
        suite("Reference <: Reference") {

            suite("Base", listOf(
                "Equal" to Test(TYPE, TYPE, true, invariant = true),
                "Disjoint" to Test(TYPE, Type.VOID, false),
                "Subtype" to Test(SUBTYPE, TYPE, true),
                "Supertype" to Test(SUPERTYPE, TYPE, false),
                "Grandchild" to Test(SUBTYPE, SUPERTYPE, true),
                "Grandparent" to Test(SUPERTYPE, SUBTYPE, false),
                "Any Subtype" to Test(Type.ANY, TYPE, false),
                "Any Supertype" to Test(TYPE, Type.ANY, true),
                "Dynamic Subtype" to Test(Type.DYNAMIC, TYPE, true, invariant = true),
                "Dynamic Supertype" to Test(TYPE, Type.DYNAMIC, true, invariant = true),
            )) { test(it) }

            suite("Generics", listOf(
                "Equal" to Test(Type.LIST[TYPE], Type.LIST[TYPE], true, invariant = true),
                "Base Subtype" to Test(Type.LIST[TYPE], Type.ITERABLE[TYPE], true),
                "Base Supertype" to Test(Type.ITERABLE[TYPE], Type.LIST[TYPE], false),
                "Generic Subtype" to Test(Type.LIST[SUBTYPE], Type.LIST[TYPE], false),
                "Generic Supertype" to Test(Type.LIST[SUPERTYPE], Type.LIST[TYPE], false),
                "Generic Dynamic Subtype" to Test(Type.LIST[Type.DYNAMIC], Type.LIST[TYPE], true, invariant = true),
                "Generic Dynamic Supertype" to Test(Type.LIST[TYPE], Type.LIST[Type.DYNAMIC], true, invariant = true),
            )) { test(it) }

        }

        suite("Reference <: Tuple", listOf(
            "Equal" to Test(Type.TUPLE[tuple(TYPE)], tuple(TYPE), true, invariant = true),
            // Note: Unlike Struct, Tuple is a final class hence no subtypes.
            "Base Supertype" to Test(Type.ANY, tuple(TYPE), false),
            "Base Dynamic" to Test(Type.DYNAMIC, tuple(TYPE), true, invariant = true),
            "Field Subtype" to Test(Type.TUPLE[tuple(SUBTYPE)], tuple(TYPE), true),
            "Field Supertype" to Test(Type.TUPLE[tuple(SUPERTYPE)], tuple(TYPE), false),
            "Field Dynamic" to Test(Type.TUPLE.DYNAMIC, tuple(TYPE), true, invariant = true),
        )) { test(it) }

        suite("Reference <: Struct", listOf(
            "Equal" to Test(Type.STRUCT[struct("x" to TYPE)], struct("x" to TYPE), true, invariant = true),
            "Base Subtype" to Test(STRUCT_SUBTYPE, struct("x" to TYPE), true),
            "Base Supertype" to Test(Type.ANY, struct("x" to TYPE), false),
            "Base Dynamic" to Test(Type.DYNAMIC, struct("x" to TYPE), true, invariant = true),
            "Field Subtype" to Test(Type.STRUCT[struct("x" to SUBTYPE)], struct("x" to TYPE), true),
            "Field Supertype" to Test(Type.STRUCT[struct("x" to SUPERTYPE)], struct("x" to TYPE), false),
            "Field Dynamic" to Test(Type.STRUCT.DYNAMIC, struct("x" to TYPE), true, invariant = true),
        )) { test(it) }

        suite("Reference <: Generic") {

            suite("Unbound", listOf(
                "Equal" to Test(TYPE, T, false),
                "Subtype" to Test(SUBTYPE, T, false),
                "Supertype" to Test(SUPERTYPE, T, false),
                "Dynamic" to Test(Type.DYNAMIC, T, true, invariant = true),
            )) { test(it) }

            suite("Bound", listOf(
                "Equal" to Test(TYPE, T, mapOf("T" to TYPE), true, invariant = true),
                "Subtype" to Test(SUBTYPE, T, mapOf("T" to TYPE), true),
                "Supertype" to Test(SUPERTYPE, T, mapOf("T" to TYPE), false),
                "Dynamic" to Test(Type.DYNAMIC, T, mapOf("T" to TYPE), true, invariant = true),
                "Bound Dynamic" to Test(TYPE, T, mapOf("T" to Type.DYNAMIC), true, invariant = true),
            )) { test(it) }

        }

        suite("Reference <: Variant", listOf(
            "Unbound" to Test(TYPE, variant(), true, invariant = true),
            "Upper Subtype" to Test(TYPE, variant(upper = SUBTYPE), false),
            "Upper Supertype" to Test(TYPE, variant(upper = SUPERTYPE), true, invariant = true),
            "Lower Subtype" to Test(TYPE, variant(lower = SUBTYPE), true, invariant = true),
            "Lower Supertype" to Test(TYPE, variant(lower = SUPERTYPE), false),
        )) { test(it) }

        suite("Tuple <: Reference", listOf(
            "Equal" to Test(tuple(TYPE), Type.TUPLE[tuple(TYPE)], true, invariant = true),
            "Supertype" to Test(tuple(TYPE), Type.ANY, true),
            "Dynamic" to Test(tuple(TYPE), Type.DYNAMIC, true, invariant = true),
        )) { test(it) }

        suite("Tuple <: Tuple") {

            suite("Fields", listOf(
                "Empty" to Test(tuple(), tuple(), true, invariant = true),
                "Single" to Test(tuple(TYPE), tuple(TYPE), true, invariant = true),
                "Multiple" to Test(tuple(TYPE, TYPE, TYPE), tuple(TYPE, TYPE, TYPE), true, invariant = true),
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
                "Mutable Field Equal" to Test(tuple(TYPE, mutable = true), tuple(TYPE, mutable = true), true, invariant = true),
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

        suite("Tuple <: Generic", listOf(
            "Unbound" to Test(tuple(TYPE), generic("T", tuple(TYPE)), false),
            "Bound" to Test(tuple(TYPE), T_ANY, mapOf("T" to tuple(TYPE)), true, invariant = true),
        )) { test(it) }

        suite("Tuple <: Variant", listOf(
            "Unbound" to Test(tuple(TYPE), variant(), true, invariant = true),
            "Upper Subtype" to Test(tuple(TYPE), variant(upper = tuple(SUBTYPE)), false),
            "Upper Supertype" to Test(tuple(TYPE), variant(upper = tuple(SUPERTYPE)), true, invariant = true),
            "Lower Subtype" to Test(tuple(TYPE), variant(lower = tuple(SUBTYPE)), true, invariant = true),
            "Lower Supertype" to Test(tuple(TYPE), variant(lower = tuple(SUPERTYPE)), false),
        )) { test(it) }

        suite("Struct <: Struct") {

            suite("Fields", listOf(
                "Empty" to Test(struct(), struct(), true, invariant = true),
                "Single" to Test(struct("x" to TYPE), struct("x" to TYPE), true, invariant = true),
                "Multiple" to Test(struct("x" to TYPE, "y" to TYPE, "z" to TYPE), struct("x" to TYPE, "y" to TYPE, "z" to TYPE), true, invariant = true),
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
                "Mutable Field Equal" to Test(struct("x" to TYPE, mutable = true), struct("x" to TYPE, mutable = true), true, invariant = true),
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

        suite("Struct <: Generic", listOf(
            "Unbound" to Test(struct("x" to TYPE), generic("T", struct("x" to TYPE)), false),
            "Bound" to Test(struct("x" to TYPE), T_ANY, mapOf("T" to struct("x" to TYPE)), true, invariant = true),
        )) { test(it) }

        suite("Struct <: Variant", listOf(
            "Unbound" to Test(struct("x" to TYPE), variant(), true, invariant = true),
            "Upper Subtype" to Test(struct("x" to TYPE), variant(upper = struct("x" to SUBTYPE)), false),
            "Upper Supertype" to Test(struct("x" to TYPE), variant(upper = struct("x" to SUPERTYPE)), true, invariant = true),
            "Lower Subtype" to Test(struct("x" to TYPE), variant(lower = struct("x" to SUBTYPE)), true, invariant = true),
            "Lower Supertype" to Test(struct("x" to TYPE), variant(lower = struct("x" to SUPERTYPE)), false),
        )) { test(it) }

        suite("Generic <: Generic") {

            suite("Unbound", listOf(
                "Name Equal" to Test(T, T, true, invariant = true),
                "Name Unequal" to Test(T, R, false),
            )) { test(it) }

            //TODO: Subtype Binding

            suite("Supertype Bindable", listOf(
                "Equal" to Test(T, T, mapOf(), mapOf("T" to variant(lower=T)), invariant = mapOf("T" to T)),
                "Subtype" to Test(T_SUBTYPE, T, mapOf(), mapOf("T" to variant(lower=T_SUBTYPE)), invariant = mapOf("T" to T_SUBTYPE)),
                "Supertype" to Test(T_SUPERTYPE, T, mapOf(), false),
            )) { test(it) }

            suite("Supertype Bound", listOf(
                "Equal" to Test(T, T, mapOf("T" to TYPE), true, invariant = true),
                "Subtype" to Test(T_SUBTYPE, T, mapOf("T" to TYPE), true),
                "Supertype" to Test(T_SUPERTYPE, T, mapOf("T" to TYPE), false),
                "Dynamic" to Test(T, T, mapOf("T" to Type.DYNAMIC), true, invariant = true),
                "Generic" to Test(T, T, mapOf("T" to T), true, invariant = true),
            )) { test(it) }

        }

        //List<*> <: List<Any>

        suite("Variant <: Reference", listOf(
            "Unbound" to Test(variant(), TYPE, false),
            "Unbound Any" to Test(variant(), Type.ANY, true),
            "Upper Equal" to Test(variant(upper = TYPE), TYPE, true),
            "Upper Subtype" to Test(variant(upper = SUBTYPE), TYPE, true),
            "Upper Supertype" to Test(variant(upper = SUPERTYPE), TYPE, false),
            "Lower Equal" to Test(variant(lower = TYPE), TYPE, false),
            "Lower Subtype" to Test(variant(lower = SUBTYPE), TYPE, false),
            "Lower Supertype" to Test(variant(lower = SUPERTYPE), TYPE, false),
        )) { test(it) }

        suite("Variant <: Tuple", listOf(
            "Unbound" to Test(variant(), tuple(TYPE), false),
            "Upper Subtype" to Test(variant(upper = tuple(SUBTYPE)), tuple(TYPE), true),
            "Upper Supertype" to Test(variant(upper = tuple(SUPERTYPE)), tuple(TYPE), false),
            "Lower Subtype" to Test(variant(lower = tuple(SUBTYPE)), tuple(TYPE), false),
            "Lower Supertype" to Test(variant(lower = tuple(SUPERTYPE)), tuple(TYPE), false),
        )) { test(it) }

        suite("Variant <: Struct", listOf(
            "Unbound" to Test(variant(), struct("x" to TYPE), false),
            "Upper Subtype" to Test(variant(upper = struct("x" to SUBTYPE)), struct("x" to TYPE), true),
            "Upper Supertype" to Test(variant(upper = struct("x" to SUPERTYPE)), struct("x" to TYPE), false),
            "Lower Subtype" to Test(variant(lower = struct("x" to SUBTYPE)), struct("x" to TYPE), false),
            "Lower Supertype" to Test(variant(lower = struct("x" to SUPERTYPE)), struct("x" to TYPE), false),
        )) { test(it) }

        suite("Variant <: Variant") {

            suite("Unbound", listOf(
                "Equal" to Test(variant(), variant(), true, invariant = true),
                "Subtype Upper" to Test(variant(upper = TYPE), variant(), true, invariant = true),
                "Subtype Lower" to Test(variant(lower = TYPE), variant(), true, invariant = true),
                "Supertype Upper" to Test(variant(), variant(upper = TYPE), false),
                "Supertype Lower" to Test(variant(), variant(lower = TYPE), false),
            )) { test(it) }

            suite("Upper Bound", listOf(
                "Upper Equal" to Test(variant(upper = TYPE), variant(upper = TYPE), true, invariant = true),
                "Upper Subtype" to Test(variant(upper = SUBTYPE), variant(upper = TYPE), true, invariant = true),
                "Upper Supertype" to Test(variant(upper = SUPERTYPE), variant(upper = TYPE), false),
                "Lower Equal" to Test(variant(lower = TYPE), variant(upper = TYPE), false),
                "Lower Subtype" to Test(variant(lower = SUBTYPE), variant(upper = TYPE), false),
                "Lower Supertype" to Test(variant(lower = SUPERTYPE), variant(upper = TYPE), false),
            )) { test(it) }

            suite("Lower Bound", listOf(
                "Upper Equal" to Test(variant(upper = TYPE), variant(lower = TYPE), false),
                "Upper Subtype" to Test(variant(upper = SUBTYPE), variant(lower = TYPE), false),
                "Upper Supertype" to Test(variant(upper = SUPERTYPE), variant(lower = TYPE), false),
                "Lower Equal" to Test(variant(lower = TYPE), variant(lower = TYPE), true, invariant = true),
                "Lower Subtype" to Test(variant(lower = SUBTYPE), variant(lower = TYPE), false),
                "Lower Supertype" to Test(variant(lower = SUPERTYPE), variant(lower = TYPE), true, invariant = true),
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
