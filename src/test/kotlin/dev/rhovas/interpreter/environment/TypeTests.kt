package dev.rhovas.interpreter.environment

import dev.rhovas.interpreter.library.Library
import org.junit.jupiter.api.Assertions
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.Arguments
import org.junit.jupiter.params.provider.MethodSource
import java.util.stream.Stream

class TypeTests {

    val ANY = Type.Base("Any", listOf(), listOf(), Scope(null))
    val NUMBER = Type.Base("Number", listOf(), listOf(ANY), Scope(null))
    val INTEGER = Type.Base("Integer", listOf(), listOf(NUMBER), Scope(null))
    val COLLECTION = Type.Base("Collection", listOf(Type.Generic("T", ANY)), listOf(ANY), Scope(null))
    val LIST = Type.Base("List", listOf(Type.Generic("T", ANY)), listOf(COLLECTION), Scope(null))

    @ParameterizedTest(name = "{0}")
    @MethodSource
    fun testGetFunction(name: String, type: Type, fname: String, farity: Int, expected: Type) {
        Assertions.assertEquals(expected, type.functions[fname, farity]?.returns)
    }

    fun testGetFunction(): Stream<Arguments> {
        LIST.scope.functions.define(Function.Definition("get", listOf(Pair("index", INTEGER)), Type.Generic("T", ANY)).also {
            it.implementation = { Object(Library.TYPES["Void"]!!, Unit) }
        })
        return Stream.of(
            Arguments.of("Unbound", LIST, "get", 1, Type.Generic("T", ANY)),
            Arguments.of("Bound", LIST.bind(mapOf(Pair("T", INTEGER))), "get", 1, INTEGER),
        )
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource
    fun testIsSubtypeOf(name: String, first: Type, second: Type, expected: Boolean) {
        Assertions.assertEquals(expected, first.isSubtypeOf(second))
    }

    fun testIsSubtypeOf(): Stream<Arguments> {
        return Stream.of(
            Arguments.of("Equal", NUMBER, NUMBER, true),
            Arguments.of("Subtype", INTEGER, NUMBER, true),
            Arguments.of("Supertype", NUMBER, INTEGER, false),
            Arguments.of("Grandchild", INTEGER, ANY, true),
            Arguments.of("Generic Equal", COLLECTION, COLLECTION, true),
            Arguments.of("Generic Subtype", LIST, COLLECTION, true),
            Arguments.of("Generic Supertype", COLLECTION, LIST, false),
            Arguments.of("Generic Grandchild", LIST, ANY, true),
            Arguments.of("Generic Bound Equal", Type.Reference(LIST, listOf(NUMBER)), Type.Reference(LIST, listOf(NUMBER)), true),
            Arguments.of("Generic Bound Subtype", Type.Reference(LIST, listOf(INTEGER)), Type.Reference(LIST, listOf(NUMBER)), true),
            Arguments.of("Generic Bound Supertype", Type.Reference(LIST, listOf(NUMBER)), Type.Reference(LIST, listOf(INTEGER)), false),
            Arguments.of("Generic Bound Grandchild", Type.Reference(LIST, listOf(INTEGER)), Type.Reference(LIST, listOf(ANY)), true),
        )
    }

}
