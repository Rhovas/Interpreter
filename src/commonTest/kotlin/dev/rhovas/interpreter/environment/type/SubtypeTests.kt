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

        suite("Tuple", listOf(
            "Empty" to Test(tuple(), tuple(), Subtype.INVARIANT),
            "Equal" to Test(tuple(Type.INTEGER), tuple(Type.INTEGER), Subtype.INVARIANT),
            "Extra Field" to Test(tuple(Type.INTEGER, Type.INTEGER), tuple(Type.INTEGER), Subtype.TRUE),
            "Missing Field" to Test(tuple(Type.INTEGER), tuple(Type.INTEGER, Type.INTEGER), Subtype.FALSE),
            "Field Subtype" to Test(tuple(Type.INTEGER), tuple(Type.NUMBER), Subtype.TRUE),
            "Field Supertype" to Test(tuple(Type.NUMBER), tuple(Type.INTEGER), Subtype.FALSE),
            "Field Generic" to Test(tuple(Type.INTEGER), tuple(generic("T", Type.NUMBER)), Subtype.INVARIANT),
            "Field Variant" to Test(tuple(Type.INTEGER), tuple(Type.Variant(null, Type.NUMBER)), Subtype.INVARIANT),
            "Mutable Subtype" to Test(tuple(Type.INTEGER, mutable = true), tuple(Type.INTEGER), Subtype.TRUE),
            "Mutable Supertype" to Test(tuple(Type.INTEGER), tuple(Type.INTEGER, mutable = true), Subtype.FALSE),
            "Both Mutable Equal" to Test(tuple(Type.INTEGER, mutable = true), tuple(Type.INTEGER, mutable = true), Subtype.INVARIANT),
            "Both Mutable Supertype" to Test(tuple(Type.INTEGER, mutable = true), tuple(Type.NUMBER, mutable = true), Subtype.FALSE),
            "Reference" to Test(tuple(Type.INTEGER), Type.TUPLE.GENERIC, Subtype.INVARIANT),
        )) { test(it, Type.TUPLE.GENERIC) }

        suite("Struct", listOf(
            "Empty" to Test(struct(), struct(), Subtype.INVARIANT),
            "Equal" to Test(struct("x" to Type.INTEGER), struct("x" to Type.INTEGER), Subtype.INVARIANT),
            "Extra Field" to Test(struct("x" to Type.INTEGER, "y" to Type.INTEGER), struct("x" to Type.INTEGER), Subtype.TRUE),
            "Missing Field" to Test(struct("x" to Type.INTEGER), struct("x" to Type.INTEGER, "y" to Type.INTEGER), Subtype.FALSE),
            "Field Subtype" to Test(struct("x" to Type.INTEGER), struct("x" to Type.NUMBER), Subtype.TRUE),
            "Field Supertype" to Test(struct("x" to Type.NUMBER), struct("x" to Type.INTEGER), Subtype.FALSE),
            "Field Generic" to Test(struct("x" to Type.INTEGER), struct("x" to generic("T", Type.NUMBER)), Subtype.INVARIANT),
            "Field Variant" to Test(struct("x" to Type.INTEGER), struct("x" to Type.Variant(null, Type.NUMBER)), Subtype.INVARIANT),
            "Mutable Subtype" to Test(struct("x" to Type.INTEGER, mutable = true), struct("x" to Type.INTEGER), Subtype.TRUE),
            "Mutable Supertype" to Test(struct("x" to Type.INTEGER), struct("x" to Type.INTEGER, mutable = true), Subtype.FALSE),
            "Both Mutable Equal" to Test(struct("x" to Type.INTEGER, mutable = true), struct("x" to Type.INTEGER, mutable = true), Subtype.INVARIANT),
            "Both Mutable Supertype" to Test(struct("x" to Type.INTEGER, mutable = true), struct("x" to Type.NUMBER, mutable = true), Subtype.FALSE),
            "Reference" to Test(struct("x" to Type.INTEGER), Type.STRUCT.GENERIC, Subtype.INVARIANT),
        )) { test(it, Type.STRUCT.GENERIC) }

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
        val component = Component.Interface(name)
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
