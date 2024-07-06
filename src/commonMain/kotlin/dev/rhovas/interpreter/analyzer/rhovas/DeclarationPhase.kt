package dev.rhovas.interpreter.analyzer.rhovas

import dev.rhovas.interpreter.analyzer.Analyzer
import dev.rhovas.interpreter.environment.Component
import dev.rhovas.interpreter.environment.Modifiers
import dev.rhovas.interpreter.environment.Type
import dev.rhovas.interpreter.environment.Variable
import dev.rhovas.interpreter.parser.rhovas.RhovasAst

/**
 * Phase 2/4: Declares the type generics/inherits to allow proper construction
 * of types and subtype checking.
 */
class DeclarationPhase(
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
        val component = (context.scope.types[ast.name]!! as Type.Reference).component as Component.Struct
        ast.generics.forEach {
            analyzer.require(component.generics[it.first] == null) { analyzer.error(ast,
                "Redefined generic type.",
                "The generic type ${it.first} is already defined in this component.",
            ) }
            component.generics[it.first] = Type.Generic(it.first, it.second?.let { analyzer.visit(it).type } ?: Type.ANY)
        }
        val fields = mutableMapOf<String, Variable.Declaration>()
        analyzer.analyze {
            component.generics.forEach { context.scope.types.define(it.key, it.value) }
            ast.members.filterIsInstance<RhovasAst.Member.Property>().map {
                analyzer.require(fields[it.name] == null) { analyzer.error(ast,
                    "Redefined field.",
                    "The field ${it.name} is already defined in this component.",
                ) }
                fields[it.name] = Variable.Declaration(it.name, analyzer.visit(it.type).type, it.mutable)
            }
        }
        val (_, implements) = visitInherits(ast.inherits, component)
        component.inherits.add(Type.STRUCT[Type.Struct(fields)])
        component.inherits.addAll(implements)
    }

    private fun visit(ast: RhovasAst.Component.Class) = analyzer.analyze(ast.context) {
        val component = (context.scope.types[ast.name]!! as Type.Reference).component as Component.Class
        ast.generics.forEach {
            analyzer.require(component.generics[it.first] == null) { analyzer.error(ast,
                "Redefined generic type.",
                "The generic type ${it.first} is already defined in this component.",
            ) }
            component.generics[it.first] = Type.Generic(it.first, it.second?.let { analyzer.visit(it).type } ?: Type.ANY)
        }
        val (extends, implements) = visitInherits(ast.inherits, component)
        component.inherits.add(extends ?: Type.ANY)
        component.inherits.addAll(implements)
    }

    private fun visit(ast: RhovasAst.Component.Interface) = analyzer.analyze(ast.context) {
        val component = (context.scope.types[ast.name]!! as Type.Reference).component as Component.Interface
        ast.generics.forEach {
            analyzer.require(component.generics[it.first] == null) { analyzer.error(ast,
                "Redefined generic type.",
                "The generic type ${it.first} is already defined in this component.",
            ) }
            component.generics[it.first] = Type.Generic(it.first, it.second?.let { analyzer.visit(it).type } ?: Type.ANY)
        }
        val (_, implements) = visitInherits(ast.inherits, component)
        component.inherits.addAll(implements.ifEmpty { listOf(Type.ANY) })
    }

    private fun visitInherits(inherits: List<RhovasAst.Type>, component: Component<*>): Pair<Type.Reference?, List<Type.Reference>> = analyzer.analyze {
        component.generics.forEach { context.scope.types.define(it.key, it.value) }
        var extends: Type.Reference? = null
        val implements = mutableListOf<Type.Reference>()
        inherits.forEach {
            val type = analyzer.visit(it).type.let { type ->
                type as? Type.Reference ?: throw analyzer.error(it,
                    "Invalid inheritance type.",
                    "The type ${type} cannot be inherited from as it is not a reference type.",
                )
            }
            if (component is Component.Class && extends == null && implements.isEmpty() && type.component is Component.Class) {
                analyzer.require(type.component.modifiers.inheritance in listOf(Modifiers.Inheritance.VIRTUAL, Modifiers.Inheritance.ABSTRACT)) { analyzer.error(it,
                    "Invalid class inheritance.",
                    "The type ${type} cannot be inherited from as it is not virtual/abstract.",
                ) }
                extends = type as Type.Reference
            } else {
                analyzer.require(type.component is Component.Interface) { analyzer.error(it,
                    "Invalid class inheritance.",
                    "The type ${type} cannot be inherited from here as it is not an interface.",
                ) }
                implements.add(type as Type.Reference)
            }
        }
        Pair(extends, implements)
    }

}
