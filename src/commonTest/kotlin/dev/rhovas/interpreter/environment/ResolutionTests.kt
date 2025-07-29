package dev.rhovas.interpreter.environment

import dev.rhovas.interpreter.RhovasSpec
import dev.rhovas.interpreter.environment.type.Type
import dev.rhovas.interpreter.library.Library
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class ResolutionTests : RhovasSpec() {

    enum class Subtype { TRUE, FALSE, INVARIANT }

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
                "Unbound" to Test("get", listOf(Type.LIST.component.type, Type.INTEGER), generic("T")),
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
                "Unbound" to Test(Type.LIST.component.type, "get", listOf(Type.INTEGER), generic("T")),
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
