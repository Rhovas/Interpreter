package dev.rhovas.interpreter.analyzer.rhovas

import dev.rhovas.interpreter.analyzer.Analyzer
import dev.rhovas.interpreter.environment.Component
import dev.rhovas.interpreter.environment.Modifiers
import dev.rhovas.interpreter.parser.rhovas.RhovasAst

/**
 * Phase 1/4: Registers all types that may be referenced during analysis.
 */
class RegistrationPhase(
    private val analyzer: RhovasAnalyzer,
) {

    private val context get() = analyzer.context
    private val Analyzer.Context.scope get() = this[RhovasAnalyzer.ScopeContext::class]

    fun visit(ast: RhovasAst.Source) {
        ast.statements.filterIsInstance<RhovasAst.Statement.Component>().forEach { visit(it.component) }
    }

    fun visit(ast: RhovasAst.Component) {
        when (ast) {
            is RhovasAst.Component.Struct -> visit(ast)
            is RhovasAst.Component.Class -> visit(ast)
            is RhovasAst.Component.Interface -> visit(ast)
        }
    }

    private fun visit(ast: RhovasAst.Component.Struct) = analyzer.analyze(ast.context) {
        analyzer.require(!context.scope.types.isDefined(ast.name, true)) { analyzer.error(ast,
            "Redefined type.",
            "The type ${ast.name} is already defined in this scope.",
        ) }
        analyzer.require(ast.modifiers.inheritance == Modifiers.Inheritance.FINAL) { analyzer.error(ast,
            "Invalid inheritance modifier.",
            "A struct cannot specify virtual/abstract modifiers as they cannot be inherited from.",
        ) }
        val component = Component.Struct(ast.name)
        context.scope.types.define(component.type)
    }

    private fun visit(ast: RhovasAst.Component.Class) = analyzer.analyze(ast.context) {
        analyzer.require(!context.scope.types.isDefined(ast.name, true)) { analyzer.error(ast,
            "Redefined type.",
            "The type ${ast.name} is already defined in this scope.",
        ) }
        val component = Component.Class(ast.name, ast.modifiers)
        context.scope.types.define(component.type)
    }

    private fun visit(ast: RhovasAst.Component.Interface) = analyzer.analyze(ast.context) {
        analyzer.require(!context.scope.types.isDefined(ast.name, true)) { analyzer.error(ast,
            "Redefined type.",
            "The type ${ast.name} is already defined in this scope.",
        ) }
        analyzer.require(ast.modifiers.inheritance == Modifiers.Inheritance.FINAL) { analyzer.error(ast,
            "Invalid inheritance modifier.",
            "An interface cannot specify virtual/abstract modifiers as they are always considered abstract.",
        ) }
        val component = Component.Interface(ast.name)
        context.scope.types.define(component.type)
    }

}
