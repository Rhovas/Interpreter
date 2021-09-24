package dev.rhovas.interpreter.library

import dev.rhovas.interpreter.environment.Function
import dev.rhovas.interpreter.environment.Object
import dev.rhovas.interpreter.evaluator.Evaluator
import dev.rhovas.interpreter.parser.rhovas.RhovasAst
import java.math.BigInteger

object LambdaInitializer : Library.TypeInitializer("Lambda") {

    override fun initialize() {
        type.methods.define(Function("toString", 1) { arguments ->
            val lambda = (arguments[0].value as Evaluator.Lambda)
            Object(Library.TYPES["String"]!!, "Lambda/${lambda.ast.parameters.size}#${lambda.hashCode()}")
        })
    }

}
