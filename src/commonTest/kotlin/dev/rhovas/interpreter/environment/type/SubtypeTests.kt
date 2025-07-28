package dev.rhovas.interpreter.environment.type

import dev.rhovas.interpreter.RhovasSpec
import dev.rhovas.interpreter.environment.Component
import dev.rhovas.interpreter.environment.Modifiers
import kotlin.test.assertEquals

class SubtypeTests : RhovasSpec() {

    private val SUPERTYPE = reference("Supertype", linkedMapOf(), listOf(Type.ANY))
    private val TYPE = reference("Type", linkedMapOf(), listOf(SUPERTYPE))
    private val SUBTYPE = reference("Subtype", linkedMapOf(), listOf(TYPE))
    private val DISJOINT = reference("Disjoint", linkedMapOf(), listOf(Type.ANY))
    private val STRUCT_SUBTYPE = reference("StructSubtype", linkedMapOf(), listOf(Type.STRUCT[struct("x" to TYPE)]))

    private val T = generic("T")
    private val T_TYPE = generic("T", TYPE)
    private val T_SUBTYPE = generic("T", SUBTYPE)
    private val T_SUPERTYPE = generic("T", SUPERTYPE)
    private val T_DISJOINT = generic("T", DISJOINT)
    private val R = generic("R")

    data class Test(
        val type: Type,
        val other: Type,
        val bindings: Map<String, Type>?,
        val expected: Any, // Boolean | Map<String, Type>
        val invariant: Any = false, // Boolean | Map<String, Type>
    ) {
        constructor(type: Type, other: Type, expected: Boolean, invariant: Boolean = false) : this(type, other, null, expected, invariant)
    }

    private fun test(test: Test, subtype: Boolean = false) {
        fun test(type: Type, other: Type, initialBindings: Map<String, Type>?, expected: Any) {
            if (initialBindings == null) {
                val subtype = isSubtypeOf(type, other, Bindings.None)
                assertEquals(expected, subtype, "isSubtypeOf(${type}, ${other}):")
            } else {
                val finalBindings = initialBindings.toMutableMap()
                val subtype = isSubtypeOf(type, other, if (subtype) Bindings.Subtype(finalBindings) else Bindings.Supertype(finalBindings))
                assertEquals(
                    Pair(expected != false, expected as? Map<*, *> ?: finalBindings),
                    Pair(subtype, finalBindings),
                    "isSubtypeOf(${type}, ${other}, ${initialBindings}):"
                )
            }
        }
        test(test.type, test.other, test.bindings, test.expected)
        test(Type.LIST[test.type], Type.LIST[test.other], test.bindings, test.invariant)
    }

    init {
        suite("Reference <: Reference") {

            suite("Base", listOf(
                "Equal" to Test(TYPE, TYPE, true, invariant = true),
                "Subtype" to Test(SUBTYPE, TYPE, true),
                "Supertype" to Test(SUPERTYPE, TYPE, false),
                "Grandchild" to Test(SUBTYPE, SUPERTYPE, true),
                "Grandparent" to Test(SUPERTYPE, SUBTYPE, false),
                "Disjoint" to Test(DISJOINT, TYPE, false),
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
                "Recursive Bound" to Test(Type.EQUATABLE.GENERIC, Type.EQUATABLE.GENERIC, true, invariant = true),
                "Recursive Bound Bindable" to Test(Type.EQUATABLE.GENERIC, Type.EQUATABLE.GENERIC, mapOf(), mapOf("T" to generic("T", Type.EQUATABLE.GENERIC)), invariant = mapOf("T" to generic("T", Type.EQUATABLE.GENERIC))),
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
                "Equal" to Test(TYPE, T_TYPE, false),
                "Subtype" to Test(SUBTYPE, T_TYPE, false),
                "Supertype" to Test(SUPERTYPE, T_TYPE, false),
                "Dynamic" to Test(Type.DYNAMIC, T_TYPE, true, invariant = true),
            )) { test(it) }

            suite("Bindable", listOf(
                "Equal" to Test(TYPE, T_TYPE, mapOf(), mapOf("T" to variant(lower = TYPE)), invariant = mapOf("T" to TYPE)),
                "Subtype" to Test(SUBTYPE, T_TYPE, mapOf(), mapOf("T" to variant(lower = SUBTYPE)), invariant = mapOf("T" to SUBTYPE)),
                "Supertype" to Test(SUPERTYPE, T_TYPE, mapOf(), false),
                "Dynamic" to Test(Type.DYNAMIC, T, mapOf(), mapOf("T" to variant(lower = Type.DYNAMIC)), invariant = mapOf("T" to Type.DYNAMIC)),
                "Recursive Bound" to Test(Type.STRING, generic("T", Type.EQUATABLE.GENERIC), mapOf(), mapOf("T" to variant(lower = Type.STRING)), invariant = mapOf("T" to Type.STRING)),
            )) { test(it) }

            suite("Bound", listOf(
                "Equal" to Test(TYPE, T, mapOf("T" to TYPE), true, invariant = true),
                "Subtype" to Test(SUBTYPE, T, mapOf("T" to TYPE), true),
                "Supertype" to Test(SUPERTYPE, T, mapOf("T" to TYPE), false),
                "Dynamic" to Test(Type.DYNAMIC, T, mapOf("T" to TYPE), true, invariant = true),
                "Bound Dynamic" to Test(TYPE, T, mapOf("T" to Type.DYNAMIC), true, invariant = true),
                "Bound Generic" to Test(Type.TUPLE[tuple(T)], T, mapOf("T" to tuple(T)), true, invariant = true),
                "Recursive Bound" to Test(Type.STRING, generic("T", Type.EQUATABLE.GENERIC), mapOf("T" to Type.STRING), mapOf("T" to Type.STRING), invariant = mapOf("T" to Type.STRING)),
            )) { test(it) }

            suite("Bound Variant", listOf(
                "Wildcard" to Test(TYPE, T, mapOf("T" to variant()), mapOf("T" to variant(lower = TYPE)), invariant = mapOf("T" to TYPE)),
                "Upper Subtype" to Test(SUBTYPE, T, mapOf("T" to variant(upper = TYPE)), mapOf("T" to variant(lower = SUBTYPE, upper = TYPE)), invariant = mapOf("T" to SUBTYPE)),
                "Upper Supertype" to Test(SUPERTYPE, T, mapOf("T" to variant(upper = TYPE)), false),
                "Lower Subtype" to Test(SUBTYPE, T, mapOf("T" to variant(lower = TYPE)), false),
                "Lower Supertype" to Test(SUPERTYPE, T, mapOf("T" to variant(lower = TYPE)), mapOf("T" to variant(lower = SUPERTYPE)), invariant = mapOf("T" to SUPERTYPE)),
                "Recursive Bound" to Test(Type.STRING, generic("T", Type.EQUATABLE.GENERIC), mapOf("T" to variant(lower = Type.STRING)), mapOf("T" to variant(lower = Type.STRING)), invariant = mapOf("T" to Type.STRING)),
            )) { test(it) }

        }

        suite("Reference <: Variant", listOf(
            "Unbound" to Test(TYPE, variant(), true, invariant = true),
            "Upper Subtype" to Test(TYPE, variant(upper = SUBTYPE), false),
            "Upper Supertype" to Test(TYPE, variant(upper = SUPERTYPE), true, invariant = true),
            "Upper Generic" to Test(TYPE, variant(upper = T), mapOf("T" to TYPE), true, invariant = true),
            "Lower Subtype" to Test(TYPE, variant(lower = SUBTYPE), true, invariant = true),
            "Lower Supertype" to Test(TYPE, variant(lower = SUPERTYPE), false),
            "Lower Generic" to Test(TYPE, variant(lower = T), mapOf("T" to TYPE), true, invariant = true),
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
            "Struct" to Test(tuple(TYPE), struct("0" to TYPE), false),
        )) { test(it) }

        suite("Tuple <: Generic", listOf(
            "Unbound" to Test(tuple(TYPE), generic("T", tuple(TYPE)), false),
            "Bindable" to Test(tuple(TYPE), T, mapOf(), mapOf("T" to variant(lower = Type.TUPLE[tuple(TYPE)])), mapOf("T" to Type.TUPLE[tuple(TYPE)])),
            "Bound" to Test(tuple(TYPE), T, mapOf("T" to tuple(TYPE)), true, invariant = true),
        )) { test(it) }

        suite("Tuple <: Variant", listOf(
            "Unbound" to Test(tuple(TYPE), variant(), true, invariant = true),
            "Upper Subtype" to Test(tuple(TYPE), variant(upper = tuple(SUBTYPE)), false),
            "Upper Supertype" to Test(tuple(TYPE), variant(upper = tuple(SUPERTYPE)), true, invariant = true),
            "Lower Subtype" to Test(tuple(TYPE), variant(lower = tuple(SUBTYPE)), true, invariant = true),
            "Lower Supertype" to Test(tuple(TYPE), variant(lower = tuple(SUPERTYPE)), false),
        )) { test(it) }

        suite("Struct <: Reference", listOf(
            "Equal" to Test(struct("x" to TYPE), Type.STRUCT[struct("x" to TYPE)], true, invariant = true),
            "Supertype" to Test(struct("x" to TYPE), Type.ANY, true),
            "Dynamic" to Test(struct("x" to TYPE), Type.DYNAMIC, true, invariant = true),
        )) { test(it) }

        suite("Struct <: Tuple", listOf(
            "Struct" to Test(struct("0" to TYPE), tuple(TYPE), false),
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
            "Bindable" to Test(struct("x" to TYPE), T, mapOf(), mapOf("T" to variant(lower = Type.STRUCT[struct("x" to TYPE)])), mapOf("T" to Type.STRUCT[struct("x" to TYPE)])),
            "Bound" to Test(struct("x" to TYPE), T, mapOf("T" to struct("x" to TYPE)), true, invariant = true),
        )) { test(it) }

        suite("Struct <: Variant", listOf(
            "Unbound" to Test(struct("x" to TYPE), variant(), true, invariant = true),
            "Upper Subtype" to Test(struct("x" to TYPE), variant(upper = struct("x" to SUBTYPE)), false),
            "Upper Supertype" to Test(struct("x" to TYPE), variant(upper = struct("x" to SUPERTYPE)), true, invariant = true),
            "Lower Subtype" to Test(struct("x" to TYPE), variant(lower = struct("x" to SUBTYPE)), true, invariant = true),
            "Lower Supertype" to Test(struct("x" to TYPE), variant(lower = struct("x" to SUPERTYPE)), false),
        )) { test(it) }

        suite("Generic <: Reference") {

            suite("Unbound", listOf(
                "Equal" to Test(T_TYPE, TYPE, true),
                "Subtype" to Test(T_SUBTYPE, TYPE, true),
                "Supertype" to Test(T_SUPERTYPE, TYPE, false),
                "Dynamic" to Test(T, Type.DYNAMIC, true, invariant = true),
            )) { test(it) }

            suite("Bindable", listOf(
                "Equal" to Test(T_TYPE, TYPE, mapOf(), mapOf("T" to variant(upper = TYPE)), invariant = mapOf("T" to TYPE)),
                "Subtype" to Test(T_SUBTYPE, TYPE, mapOf(), mapOf("T" to variant(upper = SUBTYPE))),
                "Supertype" to Test(T_SUPERTYPE, TYPE, mapOf(), mapOf("T" to variant(upper = TYPE)), invariant = mapOf("T" to TYPE)),
                "Dynamic" to Test(T, Type.DYNAMIC, mapOf(), mapOf("T" to Type.DYNAMIC), invariant = mapOf("T" to Type.DYNAMIC)),
                "Recursive Bound" to Test(generic("T", Type.EQUATABLE.GENERIC), Type.STRING, mapOf(), mapOf("T" to variant(upper = Type.STRING)), invariant = mapOf("T" to Type.STRING)),
            )) { test(it, subtype = true) }

            suite("Bound", listOf(
                "Equal" to Test(T, TYPE, mapOf("T" to TYPE), true, invariant = true),
                "Subtype" to Test(T, TYPE, mapOf("T" to SUBTYPE), true),
                "Supertype" to Test(T, TYPE, mapOf("T" to SUPERTYPE), false),
                "Dynamic" to Test(T, Type.DYNAMIC, mapOf("T" to TYPE), true, invariant = true),
                "Bound Dynamic" to Test(T, TYPE, mapOf("T" to Type.DYNAMIC), true, invariant = true),
                "Bound Generic" to Test(T, Type.TUPLE[tuple(T)], mapOf("T" to tuple(T)), true, invariant = true),
                "Recursive Bound" to Test(generic("T", Type.EQUATABLE.GENERIC), Type.STRING, mapOf("T" to Type.STRING), mapOf("T" to Type.STRING), invariant = mapOf("T" to Type.STRING)),
            )) { test(it, subtype = true) }

            suite("Bound Variant", listOf(
                "Wildcard" to Test(T, TYPE, mapOf("T" to variant()), mapOf("T" to variant(upper = TYPE)), invariant = mapOf("T" to TYPE)),
                "Upper Subtype" to Test(T, TYPE, mapOf("T" to variant(upper = SUBTYPE)), mapOf("T" to variant(upper = SUBTYPE))),
                "Upper Supertype" to Test(T, TYPE, mapOf("T" to variant(upper = SUPERTYPE)), mapOf("T" to variant(upper = TYPE)), invariant = mapOf("T" to TYPE)),
                "Lower Subtype" to Test(T, TYPE, mapOf("T" to variant(lower = SUBTYPE)), mapOf("T" to variant(lower = SUBTYPE, upper = TYPE)), invariant = mapOf("T" to TYPE)),
                "Lower Supertype" to Test(T, TYPE, mapOf("T" to variant(lower = SUPERTYPE)), false),
                "Recursive Bound" to Test(generic("T", Type.EQUATABLE.GENERIC), Type.STRING, mapOf("T" to variant()), mapOf("T" to variant(upper = Type.STRING)), invariant = mapOf("T" to Type.STRING)),
            )) { test(it, subtype = true) }

        }

        suite("Generic <: Tuple", listOf(
            "Unbound" to Test(generic("T", tuple(TYPE)), tuple(TYPE), true),
            "Bindable" to Test(T, tuple(TYPE), mapOf(), mapOf("T" to variant(upper = Type.TUPLE[tuple(TYPE)])), invariant = mapOf("T" to Type.TUPLE[tuple(TYPE)])),
            "Bound" to Test(T, tuple(TYPE), mapOf("T" to tuple(TYPE)), true, invariant = true),
        )) { test(it, subtype = true) }

        suite("Generic <: Struct", listOf(
            "Unbound" to Test(generic("T", struct("x" to TYPE)), struct("x" to TYPE), true),
            "Bindable" to Test(T, struct("x" to TYPE), mapOf(), mapOf("T" to variant(upper = Type.STRUCT[struct("x" to TYPE)])), invariant = mapOf("T" to Type.STRUCT[struct("x" to TYPE)])),
            "Bound" to Test(T, struct("x" to TYPE), mapOf("T" to struct("x" to TYPE)), true, invariant = true),
        )) { test(it, subtype = true) }

        suite("Generic <: Generic") {

            suite("Unbound", listOf(
                "Name Equal" to Test(T, T, true, invariant = true),
                "Name Unequal" to Test(T, R, false),
            )) { test(it) }

            suite("Subtype Bindable", listOf(
                "Equal" to Test(T_TYPE, T_TYPE, mapOf(), mapOf("T" to variant(upper = T_TYPE)), invariant = mapOf("T" to T_TYPE)),
                "Subtype" to Test(T_SUBTYPE, T_TYPE, mapOf(), false),
                "Supertype" to Test(T_SUPERTYPE, T_TYPE, mapOf(), mapOf("T" to variant(upper = T_TYPE)), invariant = mapOf("T" to T_TYPE)),
                "Disjoint" to Test(T_DISJOINT, T_TYPE, mapOf(), false),
                "Recursive Bound" to Test(generic("T", Type.EQUATABLE.GENERIC), generic("T", Type.STRING), mapOf(), mapOf("T" to variant(upper = generic("T", Type.STRING))), invariant = mapOf("T" to generic("T", Type.STRING))),
            )) { test(it, subtype = true) }

            suite("Subtype Bound", listOf(
                "Equal" to Test(T, T_TYPE, mapOf("T" to TYPE), false),
                "Subtype" to Test(T, T_TYPE, mapOf("T" to SUBTYPE), false),
                "Supertype" to Test(T, T_TYPE, mapOf("T" to SUPERTYPE), false),
                "Dynamic" to Test(T, T_TYPE, mapOf("T" to Type.DYNAMIC), true, invariant = true),
                "Generic" to Test(T, T_TYPE, mapOf("T" to T_TYPE), true, invariant = true),
                "Recursive Bound" to Test(generic("T", Type.EQUATABLE.GENERIC), generic("T", Type.STRING), mapOf("T" to Type.STRING), false),
            )) { test(it, subtype = true) }

            suite("Subtype Bound Variant", listOf(
                "Wildcard" to Test(T, T_TYPE, mapOf("T" to variant()), mapOf("T" to variant(upper = T_TYPE)), invariant = mapOf("T" to T_TYPE)),
                "Upper Subtype" to Test(T, T_TYPE, mapOf("T" to variant(upper = SUBTYPE)), false),
                "Upper Supertype" to Test(T, T_TYPE, mapOf("T" to variant(upper = SUPERTYPE)), mapOf("T" to variant(upper = T_TYPE)), invariant = mapOf("T" to T_TYPE)),
                "Upper Generic" to Test(T, T_TYPE, mapOf("T" to variant(upper = T_TYPE)), mapOf("T" to variant(upper = T_TYPE)), invariant = mapOf("T" to T_TYPE)),
                "Lower Subtype" to Test(T, T_TYPE, mapOf("T" to variant(lower = SUBTYPE)), false),
                "Lower Supertype" to Test(T, T_TYPE, mapOf("T" to variant(lower = SUPERTYPE)), false),
                "Lower Generic" to Test(T, T_TYPE, mapOf("T" to variant(lower = T_TYPE)), mapOf("T" to variant(lower = T_TYPE, upper = T_TYPE)), invariant = mapOf("T" to T_TYPE)),
                "Recursive Bound" to Test(generic("T", Type.EQUATABLE.GENERIC), generic("T", Type.STRING), mapOf("T" to variant(upper = Type.STRING)), mapOf("T" to variant(upper = generic("T", Type.STRING))), invariant = mapOf("T" to generic("T", Type.STRING))),
            )) { test(it, subtype = true) }

            suite("Supertype Bindable", listOf(
                "Equal" to Test(T_TYPE, T_TYPE, mapOf(), mapOf("T" to variant(lower = T_TYPE)), invariant = mapOf("T" to T_TYPE)),
                "Subtype" to Test(T_SUBTYPE, T_TYPE, mapOf(), mapOf("T" to variant(lower = T_SUBTYPE)), invariant = mapOf("T" to T_SUBTYPE)),
                "Supertype" to Test(T_SUPERTYPE, T_TYPE, mapOf(), false),
                "Disjoint" to Test(T_DISJOINT, T_TYPE, mapOf(), false),
                "Recursive Bound" to Test(generic("T", Type.STRING), generic("T", Type.EQUATABLE.GENERIC), mapOf(), mapOf("T" to variant(lower = generic("T", Type.STRING))), invariant = mapOf("T" to generic("T", Type.STRING))),
            )) { test(it) }

            suite("Supertype Bound", listOf(
                "Equal" to Test(T_TYPE, T, mapOf("T" to TYPE), true),
                "Subtype" to Test(T_SUBTYPE, T, mapOf("T" to TYPE), true),
                "Supertype" to Test(T_SUPERTYPE, T, mapOf("T" to TYPE), false),
                "Dynamic" to Test(T, T, mapOf("T" to Type.DYNAMIC), true, invariant = true),
                "Generic" to Test(T, T, mapOf("T" to T), true, invariant = true),
                "Recursive Bound" to Test(generic("T", Type.STRING), generic("T", Type.EQUATABLE.GENERIC), mapOf("T" to Type.STRING), mapOf("T" to Type.STRING)),
            )) { test(it) }

            suite("Supertype Bound Variant", listOf(
                "Wildcard" to Test(T_TYPE, T, mapOf("T" to variant()), mapOf("T" to variant(lower = T_TYPE)), invariant = mapOf("T" to T_TYPE)),
                "Upper Subtype" to Test(T_SUBTYPE, T, mapOf("T" to variant(upper = TYPE)), mapOf("T" to variant(lower = T_SUBTYPE, upper = TYPE)), invariant = mapOf("T" to T_SUBTYPE)),
                "Upper Supertype" to Test(T_SUPERTYPE, T, mapOf("T" to variant(upper = TYPE)), false),
                "Upper Generic" to Test(T_TYPE, T, mapOf("T" to variant(upper = T_TYPE)), mapOf("T" to variant(lower = T_TYPE, upper = T_TYPE)), invariant = mapOf("T" to T_TYPE)),
                "Lower Subtype" to Test(T_SUBTYPE, T, mapOf("T" to variant(lower = TYPE)), mapOf("T" to variant(lower = TYPE)), invariant = mapOf("T" to T_SUBTYPE)),
                "Lower Supertype" to Test(T_SUPERTYPE, T, mapOf("T" to variant(lower = TYPE)), mapOf("T" to variant(lower = T_SUPERTYPE)), invariant = mapOf("T" to T_SUPERTYPE)),
                "Lower Generic" to Test(T_TYPE, T, mapOf("T" to variant(lower = T_TYPE)), mapOf("T" to variant(lower = T_TYPE)), invariant = mapOf("T" to T_TYPE)),
                "Recursive Bound" to Test(generic("T", Type.STRING), generic("T", Type.EQUATABLE.GENERIC), mapOf("T" to variant(upper = Type.STRING)), mapOf("T" to variant(lower = generic("T", Type.STRING), upper = Type.STRING)), invariant = mapOf("T" to generic("T", Type.STRING))),
            )) { test(it) }

        }

        suite("Generic <: Variant") {

            suite("Unbound", listOf(
                "Wildcard" to Test(T_TYPE, variant(), true, invariant = true),
                "Upper Subtype" to Test(T_SUBTYPE, variant(upper = TYPE), true, invariant = true),
                "Upper Supertype" to Test(T_SUPERTYPE, variant(upper = TYPE), false),
                "Upper Generic" to Test(T, variant(upper = T), true, invariant = true),
                "Lower Subtype" to Test(T_SUBTYPE, variant(lower = TYPE), false),
                "Lower Supertype" to Test(T_SUPERTYPE, variant(lower = TYPE), false),
                "Lower Generic" to Test(T, variant(lower = T), true, invariant = true),
            )) { test(it) }

            suite("Bindable", listOf(
                "Wildcard" to Test(T, variant(), mapOf(), mapOf("T" to variant()), invariant = true),
                "Wildcard Generic Bound" to Test(T_TYPE, variant(), mapOf(), mapOf("T" to variant(upper = TYPE)), invariant = true),
                "Upper Subtype" to Test(T_SUBTYPE, variant(upper = TYPE), mapOf(), mapOf("T" to variant(upper = SUBTYPE)), invariant = true),
                "Upper Supertype" to Test(T_SUPERTYPE, variant(upper = TYPE), mapOf(), mapOf("T" to variant(upper = TYPE)), invariant = true),
                "Upper Disjoint" to Test(T_DISJOINT, variant(upper = TYPE), mapOf(), false),
                "Upper Dynamic" to Test(T, variant(upper = Type.DYNAMIC), mapOf(), mapOf("T" to variant(upper = Type.DYNAMIC)), invariant = true),
                "Lower Subtype" to Test(T_SUBTYPE, variant(lower = TYPE), mapOf(), false),
                "Lower Supertype" to Test(T_SUPERTYPE, variant(lower = TYPE), mapOf(), mapOf("T" to variant(lower = TYPE, upper = SUPERTYPE)), invariant = true),
                "Lower Disjoint" to Test(T_DISJOINT, variant(lower = TYPE), mapOf(), false),
                "Lower Dynamic" to Test(T, variant(lower = Type.DYNAMIC), mapOf(), mapOf("T" to variant(lower = Type.DYNAMIC)), invariant = true),
            )) { test(it, subtype = true) }

            suite("Bound", listOf(
                "Wildcard" to Test(T, variant(), mapOf("T" to TYPE), true, invariant = true),
                "Upper Subtype" to Test(T, variant(upper = TYPE), mapOf("T" to SUBTYPE), true, invariant = true),
                "Upper Supertype" to Test(T, variant(upper = TYPE), mapOf("T" to SUPERTYPE), false),
                "Upper Generic" to Test(T, variant(upper = T), mapOf("T" to T), true, invariant = true),
                "Lower Subtype" to Test(T, variant(lower = TYPE), mapOf("T" to SUBTYPE), false),
                "Lower Supertype" to Test(T, variant(lower = TYPE), mapOf("T" to SUPERTYPE), true, invariant = true),
                "Lower Generic" to Test(T, variant(lower = T), mapOf("T" to T), true, invariant = true),
            )) { test(it, subtype = true) }

            suite("Bound Variant Wildcard", listOf(
                "Wildcard" to Test(T, variant(), mapOf("T" to variant()), mapOf("T" to variant()), invariant = true),
                "Upper" to Test(T, variant(), mapOf("T" to variant(upper = TYPE)), mapOf("T" to variant(upper = TYPE)), invariant = true),
                "Lower" to Test(T, variant(), mapOf("T" to variant(lower = TYPE)), mapOf("T" to variant(lower = TYPE)), invariant = true),
            )) { test(it, subtype = true) }

            suite("Bound Variant Upper Bound", listOf(
                "Wildcard" to Test(T, variant(upper = TYPE), mapOf("T" to variant()), mapOf("T" to variant(upper = TYPE)), invariant = true),
                "Upper Subtype" to Test(T, variant(upper = TYPE), mapOf("T" to variant(upper = SUBTYPE)), mapOf("T" to variant(upper = SUBTYPE)), invariant = true),
                "Upper Supertype" to Test(T, variant(upper = TYPE), mapOf("T" to variant(upper = SUPERTYPE)), mapOf("T" to variant(upper = TYPE)), invariant = true),
                "Lower Subtype" to Test(T, variant(upper = TYPE), mapOf("T" to variant(lower = SUBTYPE)), mapOf("T" to variant(lower = SUBTYPE, upper = TYPE)), invariant = true),
                "Lower Supertype" to Test(T, variant(upper = TYPE), mapOf("T" to variant(lower = SUPERTYPE)), false),
            )) { test(it, subtype = true) }

            suite("Bound Variant Lower Bound", listOf(
                "Wildcard" to Test(T, variant(lower = TYPE), mapOf("T" to variant()), mapOf("T" to variant(lower = TYPE)), invariant = true),
                "Upper Subtype" to Test(T, variant(lower = TYPE), mapOf("T" to variant(upper = SUBTYPE)), false),
                "Upper Supertype" to Test(T, variant(lower = TYPE), mapOf("T" to variant(upper = SUPERTYPE)), mapOf("T" to variant(lower = TYPE, upper = SUPERTYPE)), invariant = true),
                "Lower Subtype" to Test(T, variant(lower = TYPE), mapOf("T" to variant(lower = SUBTYPE)), mapOf("T" to variant(lower = TYPE)), invariant = true),
                "Lower Supertype" to Test(T, variant(lower = TYPE), mapOf("T" to variant(lower = SUPERTYPE)), mapOf("T" to variant(lower = SUPERTYPE)), invariant = true),
            )) { test(it, subtype = true) }

        }

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

        suite("Variant <: Generic") {

            suite("Unbound", listOf(
                "Wildcard" to Test(variant(), T_TYPE, false),
                "Upper Subtype" to Test(variant(upper = SUBTYPE), T_TYPE, false),
                "Upper Supertype" to Test(variant(upper = SUPERTYPE), T_TYPE, false),
                "Upper Generic" to Test(variant(upper = T), T, true),
                "Lower Subtype" to Test(variant(lower = SUBTYPE), T_TYPE, false),
                "Lower Supertype" to Test(variant(lower = SUPERTYPE), T_TYPE, false),
                "Lower Generic" to Test(variant(lower = T), T, false),
            )) { test(it) }

            suite("Bindable", listOf(
                "Wildcard" to Test(variant(), T, mapOf(), mapOf("T" to variant()), invariant = true),
                "Wildcard Generic Bound" to Test(variant(), T_TYPE, mapOf(), false),
                "Upper Subtype" to Test(variant(upper = SUBTYPE), T_TYPE, mapOf(), mapOf("T" to variant(upper = SUBTYPE)), invariant = true),
                "Upper Supertype" to Test(variant(upper = SUPERTYPE), T_TYPE, mapOf(), false),
                "Upper Disjoint" to Test(variant(upper = DISJOINT), T_TYPE, mapOf(), false),
                "Upper Dynamic" to Test(variant(upper = Type.DYNAMIC), T_TYPE, mapOf(), mapOf("T" to variant(upper = Type.DYNAMIC)), invariant = true),
                "Lower Subtype" to Test(variant(lower = SUBTYPE), T_TYPE, mapOf(), false),
                "Lower Supertype" to Test(variant(lower = SUPERTYPE), T_TYPE, mapOf(), false),
                "Lower Disjoint" to Test(variant(lower = DISJOINT), T_TYPE, mapOf(), false),
                "Lower Dynamic" to Test(variant(lower = Type.DYNAMIC), T_TYPE, mapOf(), false),
            )) { test(it) }

            suite("Bound", listOf(
                "Wildcard" to Test(variant(), T, mapOf("T" to TYPE), false),
                "Upper Subtype" to Test(variant(upper = SUBTYPE), T, mapOf("T" to TYPE), true),
                "Upper Supertype" to Test(variant(upper = SUPERTYPE), T, mapOf("T" to TYPE), false),
                "Upper Generic" to Test(variant(upper = T), T, mapOf("T" to T), true),
                "Lower Subtype" to Test(variant(lower = SUBTYPE), T, mapOf("T" to TYPE), false),
                "Lower Supertype" to Test(variant(lower = SUPERTYPE), T, mapOf("T" to TYPE), false),
                "Lower Generic" to Test(variant(lower = T), T, mapOf("T" to T), false),
            )) { test(it) }

            suite("Bound Variant Wildcard", listOf(
                "Wildcard" to Test(variant(), T, mapOf("T" to variant()), mapOf("T" to variant(lower = Type.ANY)), invariant = mapOf("T" to variant(lower = variant(), upper = variant()))),
                "Upper" to Test(variant(upper = TYPE), T, mapOf("T" to variant()), mapOf("T" to variant(lower = TYPE)), invariant = mapOf("T" to variant(lower = variant(upper = TYPE), upper = variant()))),
                "Lower" to Test(variant(lower = TYPE), T, mapOf("T" to variant()), mapOf("T" to variant(lower = Type.ANY)), invariant = mapOf("T" to variant(lower = variant(lower = TYPE), upper = variant()))),
            )) { test(it) }

            suite("Bound Variant Upper Bound", listOf(
                "Wildcard" to Test(variant(), T, mapOf("T" to variant(upper = TYPE)), false),
                "Upper Subtype" to Test(variant(upper = SUBTYPE), T, mapOf("T" to variant(upper = TYPE)), mapOf("T" to variant(lower = SUBTYPE, upper = TYPE)), invariant = mapOf("T" to variant(lower = variant(upper = SUBTYPE), upper = variant(upper = TYPE)))),
                "Upper Supertype" to Test(variant(upper = SUPERTYPE), T, mapOf("T" to variant(upper = TYPE)), false),
                "Lower Subtype" to Test(variant(lower = SUBTYPE), T, mapOf("T" to variant(upper = TYPE)), false),
                "Lower Supertype" to Test(variant(lower = SUPERTYPE), T, mapOf("T" to variant(upper = TYPE)), false),
            )) { test(it) }

            suite("Bound Variant Lower Bound", listOf(
                "Wildcard" to Test(variant(), T, mapOf("T" to variant(lower = TYPE)), mapOf("T" to variant(lower = Type.ANY))),
                "Upper Subtype" to Test(variant(upper = SUBTYPE), T, mapOf("T" to variant(lower = TYPE)), mapOf("T" to variant(lower = TYPE))),
                "Upper Supertype" to Test(variant(upper = SUPERTYPE), T, mapOf("T" to variant(lower = TYPE)), mapOf("T" to variant(lower = SUPERTYPE))),
                "Lower Subtype" to Test(variant(lower = SUBTYPE), T, mapOf("T" to variant(lower = TYPE)), mapOf("T" to variant(lower = Type.ANY))),
                "Lower Supertype" to Test(variant(lower = SUPERTYPE), T, mapOf("T" to variant(lower = TYPE)), mapOf("T" to variant(lower = Type.ANY)), invariant = mapOf("T" to variant(lower = variant(lower = SUPERTYPE), upper = variant(lower = TYPE)))),
            )) { test(it) }

        }

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
                "Generic" to Test(variant(upper = TYPE), variant(upper = T), mapOf("T" to TYPE), true, invariant = true),
            )) { test(it) }

            suite("Lower Bound", listOf(
                "Upper Equal" to Test(variant(upper = TYPE), variant(lower = TYPE), false),
                "Upper Subtype" to Test(variant(upper = SUBTYPE), variant(lower = TYPE), false),
                "Upper Supertype" to Test(variant(upper = SUPERTYPE), variant(lower = TYPE), false),
                "Lower Equal" to Test(variant(lower = TYPE), variant(lower = TYPE), true, invariant = true),
                "Lower Subtype" to Test(variant(lower = SUBTYPE), variant(lower = TYPE), false),
                "Lower Supertype" to Test(variant(lower = SUPERTYPE), variant(lower = TYPE), true, invariant = true),
                "Generic" to Test(variant(lower = TYPE), variant(lower = T), mapOf("T" to TYPE), true, invariant = true),
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
