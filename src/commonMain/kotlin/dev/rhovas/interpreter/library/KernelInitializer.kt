package dev.rhovas.interpreter.library

import com.ionspin.kotlin.bignum.integer.BigInteger
import dev.rhovas.interpreter.INTERPRETER
import dev.rhovas.interpreter.environment.Component
import dev.rhovas.interpreter.environment.Object
import dev.rhovas.interpreter.environment.Type
import dev.rhovas.interpreter.parser.rhovas.RhovasAst

object KernelInitializer: Library.ComponentInitializer(Component.Class("Kernel")) {

    override fun declare() {}

    override fun define() {
        function("input",
            parameters = listOf(),
            returns = Type.STRING,
        ) { _: Unit ->
            Object(Type.STRING, INTERPRETER.stdin())
        }

        function("input",
            parameters = listOf("prompt" to Type.STRING),
            returns = Type.STRING,
        ) { (prompt): T1<String> ->
            INTERPRETER.stdout(prompt)
            Object(Type.STRING, INTERPRETER.stdin())
        }

        function("print",
            parameters = listOf("object" to Type.ANY),
            returns = Type.VOID,
        ) { (obj): T1<Object> ->
            INTERPRETER.stdout(obj.methods.toString())
            Object(Type.VOID, null)
        }

        function("range",
            parameters = listOf("lower" to Type.INTEGER, "upper" to Type.INTEGER, "bound" to Type.ATOM),
            returns = Type.LIST[Type.INTEGER],
        ) { (lower, upper, bound): T3<BigInteger, BigInteger, RhovasAst.Atom> ->
            val start = if (bound.name in listOf("incl", "incl_excl")) lower else lower.add(BigInteger.ONE)
            val end = if (bound.name in listOf("incl", "excl_incl")) upper.add(BigInteger.ONE) else upper
            Object(Type.LIST[Type.INTEGER], generateSequence(start.takeIf { it < end }) {
                it.add(BigInteger.ONE).takeIf { it < end }
            }.toList().map { Object(Type.INTEGER, it) })
        }

        function("lambda",
            generics = listOf(generic("T", Type.TUPLE.DYNAMIC), generic("R")),
            parameters = listOf("lambda" to Type.LAMBDA[generic("T"), generic("R"), Type.DYNAMIC]),
            returns = Type.LAMBDA[generic("T"), generic("R"), Type.DYNAMIC],
        ) { (lambda): T1<Object> ->
            lambda
        }

        function("regex",
            parameters = listOf("literals" to Type.LIST[Type.STRING], "arguments" to Type.LIST.DYNAMIC),
            returns = Type.REGEX,
        ) { (literals, arguments): T2<List<Object>, List<Object>> ->
            val pattern = literals.zip(arguments + listOf(null)).mapIndexed { index, (literal, argument) ->
                //TODO(#16): Union type for String | Regex
                literal.value as String + when {
                    argument == null -> ""
                    argument.type.isSubtypeOf(Type.STRING) -> Regex.escape(argument.value as String)
                    argument.type.isSubtypeOf(Type.REGEX) -> (argument.value as Regex).pattern
                    else -> throw error(
                        "Invalid argument.",
                        "The native function #regex requires argument ${index} to be type String | Regex, but received ${argument.type}.",
                    )
                }
            }.joinToString("").trim(' ').removeSurrounding("/")
            Object(Type.REGEX, Regex(pattern))
        }

        function("typeof",
            parameters = listOf("element" to Type.ANY),
            returns = Type.TYPE.DYNAMIC,
        ) { (element): T1<Object> ->
            Object(Type.TYPE[element.type], element.type)
        }
    }

}
