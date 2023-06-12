package dev.rhovas.interpreter.environment

import dev.rhovas.interpreter.RhovasSpec
import dev.rhovas.interpreter.library.Library
import kotlin.test.assertEquals

class TypeTests : RhovasSpec() {

    //Not ideal, but based on the old tests and can't be trivially updated.
    private val Type.Companion.NUMBER by lazy {
        Type.Base("Number", Scope.Definition(null)).reference.also {
            it.base.inherit(Type.COMPARABLE[it])
            Library.SCOPE.types.define(it)
            Type.INTEGER.base.inherit(it)
        }
    }

    init {
        suite("Resolution") {
            data class Test(val name: String, val arguments: List<Type>, val returns: Type?)

            suite("Function", listOf(
                "Equal" to Test("number", listOf(Type.NUMBER), Type.ANY),
                "Subtype" to Test("number", listOf(Type.INTEGER), Type.ANY),
                "Supertype" to Test("number", listOf(Type.ANY), null),
                "Generic Unbound" to Test("get", listOf(Type.LIST.GENERIC, Type.INTEGER), Type.Generic("T", Type.ANY)),
                "Generic Bound" to Test("get", listOf(Type.LIST[Type.INTEGER], Type.INTEGER), Type.INTEGER),
                "Generic Multiple" to Test("set", listOf(Type.LIST[Type.INTEGER], Type.INTEGER, Type.INTEGER), Type.INTEGER),
                "Generic Multiple Primitive Subtype First" to Test("set2", listOf(Type.INTEGER, Type.INTEGER, Type.LIST[Type.NUMBER]), Type.NUMBER),
                "Generic Multiple Primitive Subtype Second" to Test("set2", listOf(Type.NUMBER, Type.INTEGER, Type.LIST[Type.INTEGER]), null),
                "Generic Multiple Generic Subtype First" to Test("set", listOf(Type.LIST[Type.INTEGER], Type.INTEGER, Type.NUMBER), null),
                "Generic Multiple Generic Subtype Second" to Test("set", listOf(Type.LIST[Type.NUMBER], Type.INTEGER, Type.INTEGER), Type.NUMBER),
                "Generic Multiple Dynamic First" to Test("set", listOf(Type.LIST[Type.DYNAMIC], Type.INTEGER, Type.INTEGER), Type.DYNAMIC),
                "Generic Multiple Dynamic Second" to Test("set", listOf(Type.LIST[Type.INTEGER], Type.INTEGER, Type.DYNAMIC), Type.DYNAMIC),
                "Generic Multiple Mismatch" to Test("set", listOf(Type.LIST[Type.INTEGER], Type.INTEGER, Type.LIST[Type.DYNAMIC]), null),
            )) {
                val scope = Scope.Declaration(null)
                scope.functions.define(Function.Declaration("number", listOf(), listOf(Variable.Declaration("number", Type.NUMBER, false)), Type.ANY, listOf()))
                scope.functions.define(Function.Declaration("get", listOf(Type.Generic("T", Type.ANY)), listOf(Variable.Declaration("list", Type.LIST[Type.Generic("T", Type.ANY)], false), Variable.Declaration("index", Type.INTEGER, false)), Type.Generic("T", Type.ANY), listOf()))
                scope.functions.define(Function.Declaration("set", listOf(Type.Generic("T", Type.ANY)), listOf(Variable.Declaration("list", Type.LIST[Type.Generic("T", Type.ANY)], false), Variable.Declaration("index", Type.INTEGER, false), Variable.Declaration("value", Type.Generic("T", Type.ANY), false)), Type.Generic("T", Type.ANY), listOf()))
                scope.functions.define(Function.Declaration("set2", listOf(Type.Generic("T", Type.ANY)), listOf(Variable.Declaration("value", Type.Generic("T", Type.ANY), false), Variable.Declaration("index", Type.INTEGER, false), Variable.Declaration("list", Type.LIST[Type.Generic("T", Type.ANY)], false)), Type.Generic("T", Type.ANY), listOf()))
                assertEquals(it.returns, scope.functions[it.name, it.arguments]?.returns)
            }

            suite("Method", listOf(
                "Equal" to Test("<=>", listOf(Type.NUMBER, Type.NUMBER), Type.INTEGER),
                "Subtype" to Test("<=>", listOf(Type.NUMBER, Type.INTEGER), Type.INTEGER),
                "Supertype" to Test("<=>", listOf(Type.NUMBER, Type.ANY), null),
                "Dynamic" to Test("undefined", listOf(Type.DYNAMIC, Type.ANY), Type.DYNAMIC),
                "Generic Unbound" to Test("get", listOf(Type.LIST.GENERIC, Type.INTEGER), Type.Generic("T", Type.ANY)),
                "Generic Bound" to Test("get", listOf(Type.LIST[Type.INTEGER], Type.INTEGER), Type.INTEGER),
            )) { assertEquals(it.returns, it.arguments[0].methods[it.name, it.arguments.drop(1)]?.returns) }
        }

        suite("Subtype") {
            data class Test(val type: Type, val other: Type, val expected: Boolean)

            suite("Base", listOf(
                "Equal" to Test(Type.NUMBER, Type.NUMBER, true),
                "Subtype" to Test(Type.INTEGER, Type.NUMBER, true),
                "Supertype" to Test(Type.NUMBER, Type.INTEGER, false),
                "Grandchild" to Test(Type.INTEGER, Type.ANY, true),
                "Dynamic Subtype" to Test(Type.DYNAMIC, Type.NUMBER, true),
                "Dynamic Supertype" to Test(Type.NUMBER, Type.DYNAMIC, true),
            )) { assertEquals(it.expected, it.type.isSubtypeOf(it.other)) }

            suite("Tuple", listOf(
                "Empty" to Test(Type.Tuple(listOf()), Type.Tuple(listOf()), true),
                "Equal" to Test(Type.Tuple(listOf(Variable.Declaration("0", Type.INTEGER, false))), Type.Tuple(listOf(Variable.Declaration("0", Type.INTEGER, false))), true),
                "Extra Field" to Test(Type.Tuple(listOf(Variable.Declaration("0", Type.INTEGER, false), Variable.Declaration("1", Type.INTEGER, false))), Type.Tuple(listOf(Variable.Declaration("0", Type.INTEGER, false))), true),
                "Missing Field" to Test(Type.Tuple(listOf(Variable.Declaration("0", Type.INTEGER, false))), Type.Tuple(listOf(Variable.Declaration("0", Type.INTEGER, false), Variable.Declaration("1", Type.INTEGER, false))), false),
                "Field Subtype" to Test(Type.Tuple(listOf(Variable.Declaration("0", Type.INTEGER, false))), Type.Tuple(listOf(Variable.Declaration("0", Type.NUMBER, false))), false),
                "Field Supertype" to Test(Type.Tuple(listOf(Variable.Declaration("0", Type.NUMBER, false))), Type.Tuple(listOf(Variable.Declaration("0", Type.INTEGER, false))), false),
                "Field Generic" to Test(Type.Tuple(listOf(Variable.Declaration("0", Type.INTEGER, false))), Type.Tuple(listOf(Variable.Declaration("0", Type.Generic("T", Type.NUMBER), false))), true),
                "Field Variant" to Test(Type.Tuple(listOf(Variable.Declaration("0", Type.INTEGER, false))), Type.Tuple(listOf(Variable.Declaration("0", Type.Variant(null, Type.NUMBER), false))), true),
            )) {
                assertEquals(it.expected, it.type.isSubtypeOf(it.other))
                assertEquals(it.expected, Type.TUPLE[it.type].isSubtypeOf(Type.TUPLE[it.other]))
            }

            suite("Struct", listOf(
                "Empty" to Test(Type.Struct(mapOf()), Type.Struct(mapOf()), true),
                "Equal" to Test(Type.Struct(mapOf("x" to Variable.Declaration("x", Type.INTEGER, false))), Type.Struct(mapOf("x" to Variable.Declaration("x", Type.INTEGER, false))), true),
                "Extra Field" to Test(Type.Struct(mapOf("x" to Variable.Declaration("x", Type.INTEGER, false), "y" to Variable.Declaration("y", Type.INTEGER, false))), Type.Struct(mapOf("x" to Variable.Declaration("x", Type.INTEGER, false))), true),
                "Missing Field" to Test(Type.Struct(mapOf("x" to Variable.Declaration("x", Type.INTEGER, false))), Type.Struct(mapOf("x" to Variable.Declaration("x", Type.INTEGER, false), "y" to Variable.Declaration("y", Type.INTEGER, false))), false),
                "Field Subtype" to Test(Type.Struct(mapOf("x" to Variable.Declaration("x", Type.INTEGER, false))), Type.Struct(mapOf("x" to Variable.Declaration("x", Type.NUMBER, false))), false),
                "Field Supertype" to Test(Type.Struct(mapOf("x" to Variable.Declaration("x", Type.NUMBER, false))), Type.Struct(mapOf("x" to Variable.Declaration("x", Type.INTEGER, false))), false),
                "Field Generic" to Test(Type.Struct(mapOf("x" to Variable.Declaration("x", Type.INTEGER, false))), Type.Struct(mapOf("x" to Variable.Declaration("x", Type.Generic("T", Type.NUMBER), false))), true),
                "Field Variant" to Test(Type.Struct(mapOf("x" to Variable.Declaration("x", Type.INTEGER, false))), Type.Struct(mapOf("x" to Variable.Declaration("x", Type.Variant(null, Type.NUMBER), false))), true),
            )) {
                assertEquals(it.expected, it.type.isSubtypeOf(it.other))
                assertEquals(it.expected, Type.STRUCT[it.type].isSubtypeOf(Type.STRUCT[it.other]))
            }

            suite("Generic") {
                suite("Raw", listOf(
                    "Equal" to Test(Type.Generic("T", Type.NUMBER), Type.Generic("T", Type.NUMBER), true),
                    "Unequal" to Test(Type.Generic("T", Type.NUMBER), Type.Generic("R", Type.NUMBER), false),
                    "Bound Subtype" to Test(Type.Generic("T", Type.INTEGER), Type.NUMBER, true),
                    "Bound Supertype" to Test(Type.Generic("T", Type.NUMBER), Type.INTEGER, false),
                    "Bound Dynamic Subtype" to Test(Type.Generic("T", Type.DYNAMIC), Type.NUMBER, true),
                    "Bound Dynamic Supertype" to Test(Type.Generic("T", Type.NUMBER), Type.DYNAMIC, true),
                )) { assertEquals(it.expected, it.type.isSubtypeOf(it.other)) }

                suite("Unbound", listOf(
                    "Equal" to Test(Type.LIST.GENERIC, Type.LIST.GENERIC, true),
                    "Subtype" to Test(Type.LIST.GENERIC, Type.ITERABLE.GENERIC, true),
                    "Supertype" to Test(Type.ITERABLE.GENERIC, Type.LIST.GENERIC, false),
                    "Grandchild" to Test(Type.LIST.GENERIC, Type.ANY, true),
                    "Generic Subtype" to Test(Type.LIST[Type.INTEGER], Type.LIST.GENERIC, true),
                    "Generic Supertype" to Test(Type.LIST.GENERIC, Type.LIST[Type.INTEGER], false),
                    "Generic Dynamic Subtype" to Test(Type.LIST[Type.DYNAMIC], Type.LIST.GENERIC, true),
                    "Generic Dynamic Supertype" to Test(Type.LIST.GENERIC, Type.LIST[Type.DYNAMIC], true),
                )) { assertEquals(it.expected, it.type.isSubtypeOf(it.other)) }

                suite("Bound", listOf(
                    "Equal" to Test(Type.LIST[Type.NUMBER], Type.LIST[Type.NUMBER], true),
                    "Base Subtype" to Test(Type.LIST[Type.NUMBER], Type.ITERABLE[Type.NUMBER], true),
                    "Base Supertype" to Test(Type.ITERABLE[Type.NUMBER], Type.LIST[Type.NUMBER], false),
                    "Base Grandchild" to Test(Type.LIST[Type.NUMBER], Type.ANY, true),
                    "Generic Subtype" to Test(Type.LIST[Type.INTEGER], Type.LIST[Type.NUMBER], false),
                    "Generic Supertype" to Test(Type.LIST[Type.NUMBER], Type.LIST[Type.INTEGER], false),
                    "Generic Dynamic Subtype" to Test(Type.LIST[Type.DYNAMIC], Type.LIST[Type.NUMBER], true),
                    "Generic Dynamic Supertype" to Test(Type.LIST[Type.NUMBER], Type.LIST[Type.DYNAMIC], true),
                )) { assertEquals(it.expected, it.type.isSubtypeOf(it.other)) }

                suite("Variant", listOf(
                    "Covariant Subtype" to Test(Type.INTEGER, Type.Variant(null, Type.NUMBER), true),
                    "Covariant Supertype" to Test(Type.ANY, Type.Variant(null, Type.NUMBER), false),
                    "Contravariant Subtype" to Test(Type.INTEGER, Type.Variant(Type.NUMBER, null), false),
                    "Contravariant Supertype" to Test(Type.ANY, Type.Variant(Type.NUMBER, null), true),
                    "Covariant Upper Variant" to Test(Type.Variant(null, Type.NUMBER), Type.Variant(null, Type.ANY), true),
                    "Contravariant Upper Variant" to Test(Type.Variant(null, Type.NUMBER), Type.Variant(null, Type.INTEGER), false),
                    "Covariant Lower Variant" to Test(Type.Variant(Type.NUMBER, null), Type.Variant(Type.ANY, null), false),
                    "Contravariant Lower Variant" to Test(Type.Variant(Type.NUMBER, null), Type.Variant(Type.INTEGER, null), true),
                    "Equivalent Variant" to Test(Type.Variant(Type.NUMBER, Type.NUMBER), Type.Variant(Type.NUMBER, Type.NUMBER), true),
                    "Inequivalent Variant" to Test(Type.Variant(Type.NUMBER, Type.NUMBER), Type.Variant(Type.ANY, Type.INTEGER), false),
                    "Unbound Variant Subtype" to Test(Type.Variant(null, null), Type.Variant(Type.INTEGER, Type.ANY), false),
                    "Unbound Variant Supertype" to Test(Type.Variant(Type.INTEGER, Type.ANY), Type.Variant(null, null), true),
                )) {
                    assertEquals(it.expected, it.type.isSubtypeOf(it.other))
                    assertEquals(it.expected, Type.LIST[it.type].isSubtypeOf(Type.LIST[it.other]))
                }
            }
        }
    }

}
