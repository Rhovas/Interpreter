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

class TypeTests : RhovasSpec() {

    //Not ideal, but based on the old tests and can't be trivially updated.
    private val Type.Companion.NUMBER by lazy {
        val component = Component.Class("Number", Modifiers(Modifiers.Inheritance.ABSTRACT))
        component.inherits.add(Type.COMPARABLE[component.type])
        component.inherits.forEach { component.inherit(it) }
        Type.INTEGER.component.inherits.add(0, component.type)
        Type.DECIMAL.component.inherits.add(0, component.type)
        Library.SCOPE.types.define(component.name, component.type)
        component.type
    }

    init {
        suite("Resolution") {
            suite("Function") {
                data class Test(val name: String, val arguments: List<Type>, val returns: Type?)

                fun test(test: Test, initializer: (Scope.Declaration) -> Unit = {}) {
                    val scope = Scope.Declaration(null).also(initializer)
                    assertEquals(test.returns, scope.functions[test.name, test.arguments]?.returns)
                }

                suite("Subtyping", listOf(
                    "Equal" to Test("number", listOf(Type.NUMBER), Type.ANY),
                    "Subtype" to Test("number", listOf(Type.INTEGER), Type.ANY),
                    "Supertype" to Test("number", listOf(Type.ANY), null),
                )) { test(it) {
                    it.functions.define(Function.Declaration("number",
                        parameters = listOf(Variable.Declaration("number", Type.NUMBER)),
                        returns = Type.ANY,
                    ))
                } }

                suite("Generics", listOf(
                    "Unbound" to Test("get", listOf(Type.LIST.GENERIC, Type.INTEGER), generic("T")),
                    "Bound" to Test("get", listOf(Type.LIST[Type.INTEGER], Type.INTEGER), Type.INTEGER),
                    "Dynamic" to Test("get", listOf(Type.LIST[Type.DYNAMIC], Type.INTEGER), Type.DYNAMIC),
                )) { test(it) {
                    it.functions.define(Function.Declaration("get",
                        generics = linkedMapOf("T" to generic("T")),
                        parameters = listOf(Variable.Declaration("list", Type.LIST[generic("T")]), Variable.Declaration("index", Type.INTEGER)),
                        returns = generic("T"),
                    ))
                } }

                suite("Variance", listOf(
                    "Equal" to Test("set", listOf(Type.LIST[Type.NUMBER], Type.INTEGER, Type.NUMBER), Type.NUMBER),
                    "Mismatch" to Test("set", listOf(Type.LIST[Type.INTEGER], Type.INTEGER, Type.DECIMAL), null),
                    "Subtype Variant First" to Test("set", listOf(Type.LIST[Type.NUMBER], Type.INTEGER, Type.INTEGER), Type.NUMBER),
                    "Subtype Primitive First" to Test("set2", listOf(Type.INTEGER, Type.INTEGER, Type.LIST[Type.NUMBER]), Type.NUMBER),
                    "Supertype Variant First" to Test("set", listOf(Type.LIST[Type.NUMBER], Type.INTEGER, Type.ANY), null),
                    "Supertype Primitive First" to Test("set2", listOf(Type.ANY, Type.INTEGER, Type.LIST[Type.NUMBER]), null),
                    "Dynamic Variant First" to Test("set", listOf(Type.LIST[Type.NUMBER], Type.INTEGER, Type.DYNAMIC), Type.NUMBER),
                    "Dynamic Primitive First" to Test("set2", listOf(Type.DYNAMIC, Type.INTEGER, Type.LIST[Type.NUMBER]), Type.NUMBER),
                    "Dynamic Generic Variant First" to Test("set", listOf(Type.LIST[Type.DYNAMIC], Type.INTEGER, Type.NUMBER), Type.DYNAMIC),
                    "Dynamic Generic Primitive First" to Test("set2", listOf(Type.NUMBER, Type.INTEGER, Type.LIST[Type.DYNAMIC]), Type.DYNAMIC),
                )) { test(it) {
                    it.functions.define(Function.Declaration("set",
                        generics = linkedMapOf("T" to generic("T")),
                        parameters = listOf(Variable.Declaration("list", Type.LIST[generic("T")]), Variable.Declaration("index", Type.INTEGER), Variable.Declaration("value", generic("T"))),
                        returns = generic("T"),
                    ))
                    it.functions.define(Function.Declaration("set2",
                        generics = linkedMapOf("T" to generic("T")),
                        parameters = listOf(Variable.Declaration("value", generic("T")), Variable.Declaration("index", Type.INTEGER), Variable.Declaration("list", Type.LIST[generic("T")])),
                        returns = generic("T"),
                    ))
                } }
            }

            suite("Method") {
                data class Test(val type: Type, val name: String, val arguments: List<Type>, val returns: Type?)

                fun test(test: Test) {
                    assertEquals(test.returns, test.type.methods[test.name, test.arguments]?.returns)
                    assertTrue(test.returns == null || test.type.functions[test.name, test.arguments.size + 1].isNotEmpty())
                }

                suite("Reference", listOf(
                    "Equal" to Test(Type.NUMBER, "<=>", listOf(Type.NUMBER), Type.INTEGER),
                    "Subtype" to Test(Type.NUMBER, "<=>", listOf(Type.INTEGER), Type.INTEGER),
                    "Supertype" to Test(Type.NUMBER, "<=>", listOf(Type.ANY), null),
                    "Dynamic" to Test(Type.DYNAMIC, "undefined", listOf(Type.DYNAMIC), Type.DYNAMIC),
                    "Unbound" to Test(Type.LIST.GENERIC, "get", listOf(Type.INTEGER), generic("T")),
                    "Bound" to Test(Type.LIST[Type.NUMBER], "get", listOf(Type.INTEGER), Type.NUMBER),
                )) { test(it) }

                suite("Tuple", listOf(
                    "Getter" to Test(tuple(Type.INTEGER), "0", listOf(), Type.INTEGER),
                    "Mutable Setter" to Test(tuple(Type.INTEGER, mutable = true), "0", listOf(Type.INTEGER), Type.VOID),
                    "Immutable Setter" to Test(tuple(Type.INTEGER), "0", listOf(Type.INTEGER), null),
                    "Invalid Index" to Test(tuple(), "0", listOf(), null),
                    "Inherited" to Test(tuple(), "is", listOf(Type.TYPE.DYNAMIC), Type.BOOLEAN),
                    "Reference" to Test(Type.TUPLE[tuple(Type.INTEGER)], "0", listOf(), Type.INTEGER),
                    "Dynamic" to Test(Type.TUPLE.DYNAMIC, "0", listOf(), Type.DYNAMIC),
                )) { test(it) }

                suite("Struct", listOf(
                    "Getter" to Test(struct("x" to Type.INTEGER), "x", listOf(), Type.INTEGER),
                    "Mutable Setter" to Test(struct("x" to Type.INTEGER, mutable = true), "x", listOf(Type.INTEGER), Type.VOID),
                    "Immutable Setter" to Test(struct("x" to Type.INTEGER), "x", listOf(Type.INTEGER), null),
                    "Invalid Field" to Test(struct(), "x", listOf(), null),
                    "Inherited" to Test(struct(), "is", listOf(Type.TYPE.DYNAMIC), Type.BOOLEAN),
                    "Reference" to Test(Type.STRUCT[struct("x" to Type.INTEGER)], "x", listOf(), Type.INTEGER),
                    "Dynamic" to Test(Type.STRUCT.DYNAMIC, "x", listOf(), Type.DYNAMIC),
                )) { test(it) }

                suite("Generic", listOf(
                    "Bound" to Test(generic("T", Type.INTEGER), "abs", listOf(), Type.INTEGER),
                    "Unbound" to Test(generic("T"), "is", listOf(Type.TYPE.DYNAMIC), Type.BOOLEAN),
                )) { test(it) }

                suite("Variant", listOf(
                    "Upper" to Test(Type.Variant(null, Type.INTEGER), "abs", listOf(), Type.INTEGER),
                    "Lower" to Test(Type.Variant(Type.INTEGER, null), "is", listOf(Type.TYPE.DYNAMIC), Type.BOOLEAN),
                    "Unbound" to Test(Type.Variant(null, null), "is", listOf(Type.TYPE.DYNAMIC), Type.BOOLEAN),
                )) { test(it) }
            }
        }

        suite("Subtype") {
            data class Test(val type: Type, val other: Type, val expected: Boolean)

            fun test(test: Test, wrapper: Type.Reference? = null) {
                assertEquals(test.expected, test.type.isSubtypeOf(test.other))
                wrapper?.let {
                    val wrappedType = Type.Reference(it.component, mapOf(it.component.generics.keys.single() to test.type))
                    val wrappedOther = Type.Reference(it.component, mapOf(it.component.generics.keys.single() to test.other))
                    assertEquals(test.expected, wrappedType.isSubtypeOf(wrappedOther))
                }
            }

            suite("Base", listOf(
                "Equal" to Test(Type.NUMBER, Type.NUMBER, true),
                "Subtype" to Test(Type.INTEGER, Type.NUMBER, true),
                "Supertype" to Test(Type.NUMBER, Type.INTEGER, false),
                "Grandchild" to Test(Type.INTEGER, Type.ANY, true),
                "Dynamic Subtype" to Test(Type.DYNAMIC, Type.NUMBER, true),
                "Dynamic Supertype" to Test(Type.NUMBER, Type.DYNAMIC, true),
            )) { test(it) }

            suite("Tuple", listOf(
                "Empty" to Test(tuple(), tuple(), true),
                "Equal" to Test(tuple(Type.INTEGER), tuple(Type.INTEGER), true),
                "Extra Field" to Test(tuple(Type.INTEGER, Type.INTEGER), tuple(Type.INTEGER), true),
                "Missing Field" to Test(tuple(Type.INTEGER), tuple(Type.INTEGER, Type.INTEGER), false),
                "Field Subtype" to Test(tuple(Type.INTEGER), tuple(Type.NUMBER), true),
                "Field Supertype" to Test(tuple(Type.NUMBER), tuple(Type.INTEGER), false),
                "Field Generic" to Test(tuple(Type.INTEGER), tuple(generic("T", Type.NUMBER)), true),
                "Field Variant" to Test(tuple(Type.INTEGER), tuple(Type.Variant(null, Type.NUMBER)), true),
                "Mutable Subtype" to Test(tuple(Type.INTEGER, mutable = true), tuple(Type.INTEGER), true),
                "Mutable Supertype" to Test(tuple(Type.INTEGER), tuple(Type.INTEGER, mutable = true), false),
                "Both Mutable Equal" to Test(tuple(Type.INTEGER, mutable = true), tuple(Type.INTEGER, mutable = true), true),
                "Both Mutable Supertype" to Test(tuple(Type.INTEGER, mutable = true), tuple(Type.NUMBER, mutable = true), false),
                "Reference" to Test(tuple(Type.INTEGER), Type.TUPLE.GENERIC, true),
            )) { test(it, Type.TUPLE.GENERIC) }

            suite("Struct", listOf(
                "Empty" to Test(struct(), struct(), true),
                "Equal" to Test(struct("x" to Type.INTEGER), struct("x" to Type.INTEGER), true),
                "Extra Field" to Test(struct("x" to Type.INTEGER, "y" to Type.INTEGER), struct("x" to Type.INTEGER), true),
                "Missing Field" to Test(struct("x" to Type.INTEGER), struct("x" to Type.INTEGER, "y" to Type.INTEGER), false),
                "Field Subtype" to Test(struct("x" to Type.INTEGER), struct("x" to Type.NUMBER), true),
                "Field Supertype" to Test(struct("x" to Type.NUMBER), struct("x" to Type.INTEGER), false),
                "Field Generic" to Test(struct("x" to Type.INTEGER), struct("x" to generic("T", Type.NUMBER)), true),
                "Field Variant" to Test(struct("x" to Type.INTEGER), struct("x" to Type.Variant(null, Type.NUMBER)), true),
                "Mutable Subtype" to Test(struct("x" to Type.INTEGER, mutable = true), struct("x" to Type.INTEGER), true),
                "Mutable Supertype" to Test(struct("x" to Type.INTEGER), struct("x" to Type.INTEGER, mutable = true), false),
                "Both Mutable Equal" to Test(struct("x" to Type.INTEGER, mutable = true), struct("x" to Type.INTEGER, mutable = true), true),
                "Both Mutable Supertype" to Test(struct("x" to Type.INTEGER, mutable = true), struct("x" to Type.NUMBER, mutable = true), false),
                "Reference" to Test(struct("x" to Type.INTEGER), Type.STRUCT.GENERIC, true),
            )) { test(it, Type.STRUCT.GENERIC) }

            suite("Generic") {
                suite("Raw", listOf(
                    "Equal" to Test(generic("T", Type.NUMBER), generic("T", Type.NUMBER), true),
                    "Unequal" to Test(generic("T", Type.NUMBER), generic("R", Type.NUMBER), false),
                    "Bound Subtype" to Test(generic("T", Type.INTEGER), Type.NUMBER, true),
                    "Bound Supertype" to Test(generic("T", Type.NUMBER), Type.INTEGER, false),
                    "Bound Dynamic Subtype" to Test(generic("T", Type.DYNAMIC), Type.NUMBER, true),
                    "Bound Dynamic Supertype" to Test(generic("T", Type.NUMBER), Type.DYNAMIC, true),
                )) { test(it) }

                suite("Unbound", listOf(
                    "Equal" to Test(Type.LIST.GENERIC, Type.LIST.GENERIC, true),
                    "Subtype" to Test(Type.LIST.GENERIC, Type.ITERABLE.GENERIC, true),
                    "Supertype" to Test(Type.ITERABLE.GENERIC, Type.LIST.GENERIC, false),
                    "Grandchild" to Test(Type.LIST.GENERIC, Type.ANY, true),
                    "Generic Subtype" to Test(Type.LIST[Type.INTEGER], Type.LIST.GENERIC, true),
                    "Generic Supertype" to Test(Type.LIST.GENERIC, Type.LIST[Type.INTEGER], false),
                    "Generic Dynamic Subtype" to Test(Type.LIST[Type.DYNAMIC], Type.LIST.GENERIC, true),
                    "Generic Dynamic Supertype" to Test(Type.LIST.GENERIC, Type.LIST[Type.DYNAMIC], true),
                )) { test(it) }

                suite("Bound", listOf(
                    "Equal" to Test(Type.LIST[Type.NUMBER], Type.LIST[Type.NUMBER], true),
                    "Base Subtype" to Test(Type.LIST[Type.NUMBER], Type.ITERABLE[Type.NUMBER], true),
                    "Base Supertype" to Test(Type.ITERABLE[Type.NUMBER], Type.LIST[Type.NUMBER], false),
                    "Base Grandchild" to Test(Type.LIST[Type.NUMBER], Type.ANY, true),
                    "Generic Subtype" to Test(Type.LIST[Type.INTEGER], Type.LIST[Type.NUMBER], false),
                    "Generic Supertype" to Test(Type.LIST[Type.NUMBER], Type.LIST[Type.INTEGER], false),
                    "Generic Dynamic Subtype" to Test(Type.LIST[Type.DYNAMIC], Type.LIST[Type.NUMBER], true),
                    "Generic Dynamic Supertype" to Test(Type.LIST[Type.NUMBER], Type.LIST[Type.DYNAMIC], true),
                )) { test(it) }
            }

            suite("Variant") {
                suite("Covariant", listOf(
                    "Subtype" to Test(Type.INTEGER, Type.Variant(null, Type.NUMBER), true),
                    "Supertype" to Test(Type.ANY, Type.Variant(null, Type.NUMBER), false),
                    "Inverse Subtype" to Test(Type.Variant(null, Type.NUMBER), Type.INTEGER, false),
                    "Inverse Supertype" to Test(Type.Variant(null, Type.NUMBER), Type.ANY,true),
                    "Variant Subtype" to Test(Type.Variant(null, Type.NUMBER), Type.Variant(null, Type.INTEGER), false),
                    "Variant Supertype" to Test(Type.Variant(null, Type.NUMBER), Type.Variant(null, Type.ANY), true),
                    "Reference" to Test(Type.Variant(null, Type.NUMBER), Type.ANY, true),
                )) { test(it, Type.LIST.GENERIC) }

                suite("Contravariant", listOf(
                    "Subtype" to Test(Type.INTEGER, Type.Variant(Type.NUMBER, null), false),
                    "Supertype" to Test(Type.ANY, Type.Variant(Type.NUMBER, null), true),
                    "Inverse Subtype" to Test(Type.Variant(Type.NUMBER, null), Type.INTEGER, false),
                    "Inverse Supertype" to Test(Type.Variant(Type.NUMBER, null), Type.ANY,true),
                    "Variant Subtype" to Test(Type.Variant(Type.NUMBER, null), Type.Variant(Type.INTEGER, null), true),
                    "Variant Supertype" to Test(Type.Variant(Type.NUMBER, null), Type.Variant(Type.ANY, null), false),
                    "Reference" to Test(Type.Variant(Type.NUMBER, null), Type.ANY, true),
                )) { test(it, Type.LIST.GENERIC) }

                suite("Unbound", listOf(
                    "Type" to Test(Type.NUMBER, Type.Variant(null, null), true),
                    "Inverse Type" to Test(Type.Variant(null, null), Type.NUMBER, false),
                    "Variant Subtype" to Test(Type.Variant(Type.INTEGER, Type.ANY), Type.Variant(null, null), true),
                    "Variant Supertype" to Test(Type.Variant(null, null), Type.Variant(Type.INTEGER, Type.ANY), false),
                    "Reference" to Test(Type.Variant(null, null), Type.ANY, true),
                )) { test(it, Type.LIST.GENERIC) }
            }
        }

        suite("Unification") {
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
