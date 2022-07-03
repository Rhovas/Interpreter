package dev.rhovas.interpreter.library

import dev.rhovas.interpreter.EVALUATOR
import dev.rhovas.interpreter.environment.Object

@Reflect.Type("Nullable", [Reflect.Type("T")])
object NullableInitializer : Library.TypeInitializer("Nullable") {

    override fun initialize() {
        inherits.add(Library.TYPES["Any"]!!)
    }

    @Reflect.Method("get", returns = Reflect.Type("T"))
    fun get(instance: Object?): Object {
        return instance ?: throw EVALUATOR.error(
            null,
            "Invalid Nullable access",
            "The value was not defined.",
        )
    }

    @Reflect.Method("equals", operator = "==",
        parameters = [Reflect.Type("Nullable", [Reflect.Type("T")])],
        returns = Reflect.Type("Boolean"),
    )
    fun equals(instance: Object?, other: Object?): Boolean {
        return when {
            instance == null || other == null -> instance == other
            else -> {
                val method = instance.methods["==", listOf(instance.type)] ?: throw EVALUATOR.error(
                    null,
                    "Undefined method.",
                    "The method ${instance.type.base.name}.==(${instance.type}) is undefined.",
                )
                when {
                    other.type.isSubtypeOf(method.parameters[0].second) -> method.invoke(listOf(other)).value as Boolean
                    else -> false
                }
            }
        }
    }

    @Reflect.Method("toString", returns = Reflect.Type("String"))
    fun toString(instance: Object?): String {
        return when (instance) {
            null -> "null"
            else -> instance.methods["toString", listOf()]!!.invoke(listOf()).value as String
        }
    }

}
