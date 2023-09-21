package dev.rhovas.interpreter.library

import com.ionspin.kotlin.bignum.decimal.BigDecimal
import com.ionspin.kotlin.bignum.integer.BigInteger
import dev.rhovas.interpreter.environment.Component
import dev.rhovas.interpreter.environment.Object
import dev.rhovas.interpreter.environment.Type
import dev.rhovas.interpreter.parser.Input
import dev.rhovas.interpreter.parser.ParseException
import dev.rhovas.interpreter.parser.rhovas.RhovasAst
import dev.rhovas.interpreter.parser.rhovas.RhovasLexer
import dev.rhovas.interpreter.parser.rhovas.RhovasTokenType

object StringInitializer : Library.ComponentInitializer(Component.Class("String")) {

    override fun declare() {
        inherits.add(Type.COMPARABLE[Type.STRING])
        inherits.add(Type.HASHABLE[Type.STRING])
    }

    override fun define() {
        method("size",
            parameters = listOf(),
            returns = Type.INTEGER,
        ) { (instance): T1<String> ->
            Object(Type.INTEGER, BigInteger.fromInt(instance.length))
        }

        method("empty",
            parameters = listOf(),
            returns = Type.BOOLEAN,
        ) { (instance): T1<String> ->
            Object(Type.BOOLEAN, instance.isEmpty())
        }

        method("chars",
            parameters = listOf(),
            returns = Type.LIST[Type.STRING],
        ) { (instance): T1<String> ->
            Object(Type.LIST[Type.STRING], instance.toCharArray().map { Object(Type.STRING, it.toString()) })
        }

        method("get", "[]",
            parameters = listOf("index" to Type.INTEGER),
            returns = Type.STRING,
        ) { (instance, index): T2<String, BigInteger> ->
            require(index >= BigInteger.ZERO && index <= BigInteger.fromInt(instance.length)) { error(
                "Invalid index.",
                "Expected an index in range [0, ${instance.length}), but received ${index}.",
            ) }
            Object(Type.STRING, instance[index.intValue()].toString())
        }

        method("slice",
            parameters = listOf("start" to Type.INTEGER),
            returns = Type.STRING,
        ) { (instance, start): T2<String, BigInteger> ->
            require(start >= BigInteger.ZERO && start <= BigInteger.fromInt(instance.length)) { error(
                "Invalid index.",
                "Expected a start index in range [0, ${instance.length}), but received ${start}.",
            ) }
            Object(Type.STRING, instance.substring(start.intValue()))
        }

        method("slice", "[]",
            parameters = listOf("start" to Type.INTEGER, "end" to Type.INTEGER),
            returns = Type.STRING,
        ) { (instance, start, end): T3<String, BigInteger, BigInteger> ->
            require(start >= BigInteger.ZERO && start <= BigInteger.fromInt(instance.length)) { error(
                "Invalid index.",
                "Expected a start index in range [0, ${instance.length}], but received ${start}.",
            ) }
            require(end >= start && end <= BigInteger.fromInt(instance.length)) { error(
                "Invalid index.",
                "Expected an end index in range [start = ${start}, ${instance.length}], but received ${end}.",
            ) }
            Object(Type.STRING, instance.substring(start.intValue(), end.intValue()))
        }

        method("contains",
            parameters = listOf("other" to Type.STRING),
            returns = Type.BOOLEAN,
        ) { (instance, other): T2<String, String> ->
            Object(Type.BOOLEAN, instance.contains(other))
        }

        method("matches",
            parameters = listOf("regex" to Type.REGEX),
            returns = Type.BOOLEAN,
        ) { (instance, regex): T2<String, Regex> ->
            Object(Type.BOOLEAN, instance.matches(regex))
        }

        method("indexOf",
            parameters = listOf("other" to Type.STRING),
            returns = Type.NULLABLE[Type.INTEGER],
        ) { (instance, other): T2<String, String> ->
            val result = instance.indexOf(other).takeIf { it != -1 }?.let { BigInteger.fromInt(it) }
            Object(Type.NULLABLE[Type.INTEGER], result?.let { Pair(Object(Type.INTEGER, it), null) })
        }

        method("replace",
            parameters = listOf("original" to Type.STRING, "other" to Type.STRING),
            returns = Type.STRING,
        ) { (instance, original, other): T3<String, String, String> ->
            Object(Type.STRING, instance.replace(original, other))
        }

        method("concat", operator = "+",
            parameters = listOf("other" to Type.STRING),
            returns = Type.STRING,
        ) { (instance, other): T2<String, String> ->
            Object(Type.STRING, instance + other)
        }

        method("repeat", operator = "*",
            parameters = listOf("times" to Type.INTEGER),
            returns = Type.STRING,
        ) { (instance, times): T2<String, BigInteger> ->
            Object(Type.STRING, instance.repeat(times.intValue()))
        }

        method("reverse",
            parameters = listOf(),
            returns = Type.STRING,
        ) { (instance): T1<String> ->
            Object(Type.STRING, instance.reversed())
        }

        method("lowercase",
            parameters = listOf(),
            returns = Type.STRING,
        ) { (instance): T1<String> ->
            Object(Type.STRING, instance.lowercase())
        }

        method("uppercase",
            parameters = listOf(),
            returns = Type.STRING,
        ) { (instance): T1<String> ->
            Object(Type.STRING, instance.uppercase())
        }

        method("split",
            parameters = listOf("other" to Type.STRING),
            returns = Type.LIST[Type.STRING]
        ) { (instance, other): T2<String, String> ->
            Object(Type.LIST[Type.STRING], instance.split(other).map { Object(Type.STRING, it) })
        }

        method("to",
            parameters = listOf("type" to Type.TYPE[Type.INTEGER]),
            returns = Type.NULLABLE[Type.INTEGER],
        ) { (instance, _type): T2<String, Type> ->
            val result = try {
                val lexer = RhovasLexer(Input("String.to(Integer)", instance))
                lexer.lexToken()?.takeIf { it.type == RhovasTokenType.INTEGER && lexer.lexToken() == null }?.value as BigInteger?
            } catch (e: ParseException) { null }
            Object(Type.NULLABLE[Type.INTEGER], result?.let { Pair(Object(Type.INTEGER, it), null) })
        }

        method("to",
            parameters = listOf("type" to Type.TYPE[Type.INTEGER], "base" to Type.INTEGER),
            returns = Type.NULLABLE[Type.INTEGER],
        ) { (instance, _type, base): T3<String, Type, BigInteger> ->
            require(base >= BigInteger.TWO && base <= BigInteger.fromInt(36)) { error(
                "Invalid index.",
                "Expected a base in range [2, 36], but received ${base}.",
            ) }
            val result = try {
                BigInteger.parseString(instance, base.intValue())
            } catch (e: ArithmeticException) { null }
            Object(Type.NULLABLE[Type.INTEGER], result?.let { Pair(Object(Type.INTEGER, it), null) })
        }

        method("to",
            parameters = listOf("type" to Type.TYPE[Type.DECIMAL]),
            returns = Type.NULLABLE[Type.DECIMAL],
        ) { (instance, _type): T2<String, Type> ->
            val input = if (instance.contains(".")) instance else "${instance}.0"
            val result = try {
                val lexer = RhovasLexer(Input("String.to(Decimal)", input))
                lexer.lexToken()?.takeIf { it.type == RhovasTokenType.DECIMAL && lexer.lexToken() == null }?.value as BigDecimal?
            } catch (e: ParseException) { null }
            Object(Type.NULLABLE[Type.DECIMAL], result?.let { Pair(Object(Type.DECIMAL, it), null) })
        }

        method("to",
            parameters = listOf("type" to Type.TYPE[Type.ATOM]),
            returns = Type.ATOM,
        ) { (instance, _type): T2<String, Type> ->
            Object(Type.ATOM, RhovasAst.Atom(instance))
        }
    }

}
