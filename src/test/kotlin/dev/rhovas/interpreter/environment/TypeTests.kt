package dev.rhovas.interpreter.environment

import dev.rhovas.interpreter.library.Library
import org.junit.jupiter.api.Assertions
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.Arguments
import org.junit.jupiter.params.provider.MethodSource
import java.util.stream.Stream

class TypeTests {

    val ANY = Type.Base("Any", listOf(), listOf(), Scope(null)).reference
    val NUMBER = Type.Base("Number", listOf(), listOf(ANY), Scope(null)).reference
    val INTEGER = Type.Base("Integer", listOf(), listOf(NUMBER), Scope(null)).reference
    val COLLECTION = Type.Base("Collection", listOf(Type.Generic("T", ANY)), listOf(ANY), Scope(null)).reference
    val LIST = Type.Base("List", listOf(Type.Generic("T", ANY)), listOf(COLLECTION), Scope(null)).reference
    val DYNAMIC = Type.Base("Dynamic", listOf(), listOf(), Scope(null)).reference

    @ParameterizedTest(name = "{0}")
    @MethodSource
    fun testGetFunction(test: String, type: Type, name: String, arguments: List<Type>, expected: Type?) {
        Assertions.assertEquals(expected, type.functions[name, arguments]?.returns)
    }

    fun testGetFunction(): Stream<Arguments> {
        NUMBER.base.scope.functions.define(Function.Definition("<=>", listOf(Pair("other", NUMBER)), INTEGER).also {
            it.implementation = { Object(Library.TYPES["Void"]!!, Unit) }
        })
        LIST.base.scope.functions.define(Function.Definition("get", listOf(Pair("index", INTEGER)), Type.Generic("T", ANY)).also {
            it.implementation = { Object(Library.TYPES["Void"]!!, Unit) }
        })
        return Stream.of(
            Arguments.of("Equal", NUMBER, "<=>", listOf(NUMBER), INTEGER),
            Arguments.of("Subtype", NUMBER, "<=>", listOf(INTEGER), INTEGER),
            Arguments.of("Supertype", NUMBER, "<=>", listOf(ANY), null),
            Arguments.of("Generic Unbound", LIST, "get", listOf(INTEGER), Type.Generic("T", ANY)),
            Arguments.of("Generic Bound", LIST.bind(mapOf(Pair("T", INTEGER))), "get", listOf(INTEGER), INTEGER),
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
            Arguments.of("Generic Bound Equal", Type.Reference(LIST.base, listOf(NUMBER)), Type.Reference(LIST.base, listOf(NUMBER)), true),
            Arguments.of("Generic Bound Subtype", Type.Reference(LIST.base, listOf(INTEGER)), Type.Reference(LIST.base, listOf(NUMBER)), true),
            Arguments.of("Generic Bound Supertype", Type.Reference(LIST.base, listOf(NUMBER)), Type.Reference(LIST.base, listOf(INTEGER)), false),
            Arguments.of("Generic Bound Grandchild", Type.Reference(LIST.base, listOf(INTEGER)), Type.Reference(LIST.base, listOf(ANY)), true),
            Arguments.of("Generic Unbound", Type.Reference(LIST.base, listOf(INTEGER)), LIST, true),
            Arguments.of("Dynamic Subtype", DYNAMIC, ANY, true),
            Arguments.of("Dynamic Supertype", ANY, DYNAMIC, true),
            Arguments.of("Generic Dynamic Bound Subtype", Type.Reference(LIST.base, listOf(DYNAMIC)), LIST, true),
            Arguments.of("Generic Dynamic Bound Supertype", LIST, Type.Reference(LIST.base, listOf(DYNAMIC)), true),
        )
    }

}
