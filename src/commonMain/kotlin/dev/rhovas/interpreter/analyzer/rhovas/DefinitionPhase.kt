package dev.rhovas.interpreter.analyzer.rhovas

import dev.rhovas.interpreter.analyzer.Analyzer
import dev.rhovas.interpreter.environment.Component
import dev.rhovas.interpreter.environment.Function
import dev.rhovas.interpreter.environment.Modifiers
import dev.rhovas.interpreter.environment.Scope
import dev.rhovas.interpreter.environment.Type
import dev.rhovas.interpreter.environment.Variable
import dev.rhovas.interpreter.parser.rhovas.RhovasAst

/**
 * Phase 2/4: Declares the type methods to allow full semantic analysis. This
 * phase MUST be traversed in DAG order through the type hierarchy (in other
 * words, supertypes must be visited before subtypes).
 */
class DefinitionPhase(
    val analyzer: RhovasAnalyzer,
) {

    private val context get() = analyzer.context
    private val Analyzer.Context.scope get() = this[RhovasAnalyzer.ScopeContext::class]

    fun visit(ast: RhovasAst.Component): RhovasIr.DefinitionPhase.Component {
        return when (ast) {
            is RhovasAst.Component.Struct -> visit(ast)
            is RhovasAst.Component.Class -> visit(ast)
            is RhovasAst.Component.Interface -> visit(ast)
        }
    }

    private fun visit(ast: RhovasAst.Component.Struct): RhovasIr.DefinitionPhase.Component.Struct = analyzer.analyze(ast.context) {
        val component = (context.scope.types[ast.name]!! as Type.Reference).component as Component.Struct
        val fields = ast.members.filterIsInstance<RhovasAst.Member.Property>().map { visit(it, component) }
        component.inherits.forEach { component.inherit(it) }
        component.inherited.functions.define(Function.Definition(Function.Declaration("",
            generics = component.generics,
            parameters = listOf(Variable.Declaration("fields", Type.STRUCT[fields.filter { it.ast.value == null }.map { it.getter.name to it.getter.returns }])),
            returns = component.type,
        )))
        component.inherited.functions.define(Function.Definition(Function.Declaration("",
            generics = component.generics,
            parameters = fields.map { Variable.Declaration(it.getter.name, it.getter.returns) },
            returns = component.type,
        )))
        val members = fields + ast.members.filter { it !is RhovasAst.Member.Property }.map { visit(it, component) }
        validateConcrete(ast, component)
        RhovasIr.DefinitionPhase.Component.Struct(ast, component, members)
    }

    private fun visit(ast: RhovasAst.Component.Class): RhovasIr.DefinitionPhase.Component.Class = analyzer.analyze(ast.context) {
        val component = (context.scope.types[ast.name]!! as Type.Reference).component as Component.Class
        component.inherits.forEach { component.inherit(it) }
        val members = ast.members.map { visit(it, component) }.toMutableList()
        if (ast.modifiers.inheritance != Modifiers.Inheritance.ABSTRACT) {
            validateConcrete(ast, component)
        }
        RhovasIr.DefinitionPhase.Component.Class(ast, component, component.inherits.first().takeUnless { it == Type.ANY }, members)
    }

    private fun visit(ast: RhovasAst.Component.Interface): RhovasIr.DefinitionPhase.Component.Interface = analyzer.analyze(ast.context) {
        val component = (context.scope.types[ast.name]!! as Type.Reference).component as Component.Interface
        component.inherits.forEach { component.inherit(it) }
        val members = ast.members.map { visit(it, component) }.toMutableList()
        RhovasIr.DefinitionPhase.Component.Interface(ast, component, members)
    }

    private fun validateConcrete(ast: RhovasAst.Component, component: Component<*>) {
        component.inherited.functions.collect().values.flatten()
            .filter { it.modifiers.inheritance == Modifiers.Inheritance.ABSTRACT }
            .forEach {
                analyzer.require(component.scope.functions[it.name, listOf(component.type) + it.parameters.drop(1).map { it.type }, true] != null) { analyzer.error(ast,
                    "Missing override.",
                    "The method ${it.name}/${it.parameters.size} is abstract and not overriden in ${component.name}, which requires a definition.",
                ) }
            }
    }

    fun visit(ast: RhovasAst.Member, component: Component<*>): RhovasIr.DefinitionPhase.Member {
        return when (ast) {
            is RhovasAst.Member.Property -> visit(ast, component)
            is RhovasAst.Member.Initializer -> visit(ast, component)
            is RhovasAst.Member.Method -> visit(ast, component)
        }
    }

    fun visit(ast: RhovasAst.Member.Property, component: Component<*>): RhovasIr.DefinitionPhase.Member.Property = analyzer.analyze(ast.context) { analyzer.analyze {
        component.generics.forEach { context.scope.types.define(it.key, it.value) }
        //TODO: Validate override constraints and check setter definitions
        analyzer.require(component.scope.functions[ast.name, 1, true].isEmpty()) { analyzer.error(ast,
            "Redefined property.",
            "The property ${ast.name} is already defined in ${component.name}.",
        ) }
        val type = analyzer.visit(ast.type).type
        val getter = Function.Definition(Function.Declaration(ast.name,
            generics = component.generics,
            parameters = listOf(Variable.Declaration("this", component.type)),
            returns = type,
        ))
        val setter = if (ast.mutable) Function.Definition(Function.Declaration(ast.name,
            generics = component.generics,
            parameters = listOf(Variable.Declaration("this", component.type), Variable.Declaration("value", type)),
            returns = Type.VOID,
        )) else null
        component.scope.functions.define(getter)
        setter?.let { component.scope.functions.define(it) }
        RhovasIr.DefinitionPhase.Member.Property(ast, getter, setter)
    } }

    fun visit(ast: RhovasAst.Member.Initializer, component: Component<*>): RhovasIr.DefinitionPhase.Member.Initializer = analyzer.analyze(ast.context) { analyzer.analyze {
        component.generics.forEach { context.scope.types.define(it.key, it.value) }
        val parameters = ast.parameters.mapIndexed { index, parameter ->
            val type = parameter.second?.let { analyzer.visit(it).type } ?: throw analyzer.error(ast,
                "Missing parameter type.",
                "The initializer init/${ast.parameters.size} requires parameter ${index} to have an defined type.",
            )
            Variable.Declaration(parameter.first, type)
        }
        val returns = ast.returns?.let { analyzer.visit(it).type } ?: component.type
        val throws = ast.throws.map { analyzer.visit(it).type }
        val initializer = Function.Definition(Function.Declaration("", ast.modifiers, component.generics, parameters, returns, throws))
        analyzer.require(component.scope.functions["", ast.parameters.size, true].all { it.isDisjointWith(initializer.declaration) }) { analyzer.error(ast,
            "Redefined initializer.",
            "The initializer init/${ast.parameters.size} overlaps with an existing function in ${component.name}.",
        ) }
        component.scope.functions.define(initializer)
        RhovasIr.DefinitionPhase.Member.Initializer(ast, initializer)
    } }

    fun visit(ast: RhovasAst.Member.Method, component: Component<*>): RhovasIr.DefinitionPhase.Member.Method {
        val function = visit(ast.function, ast.modifiers, component)
        return RhovasIr.DefinitionPhase.Member.Method(ast, function)
    }

    fun visit(ast: RhovasAst.Statement.Declaration.Function, modifiers: Modifiers? = null, component: Component<*>? = null): RhovasIr.DefinitionPhase.Function = analyzer.analyze(ast.context) {
        val scope = component?.scope ?: context.scope
        analyzer.analyze {
            analyzer.require(ast.operator == null || component != null) { analyzer.error(ast,
                "Invalid operator overload.",
                "Operator overloading can only be used within component methods, not functions.",
            ) }
            val generics = component?.generics?.takeIf { ast.parameters.firstOrNull()?.second == null }?.toMap(linkedMapOf()) ?: linkedMapOf()
            ast.generics.forEach {
                analyzer.require(generics[it.first] == null) { analyzer.error(ast,
                    "Redefined generic type.",
                    "The generic type ${it.first} is already defined in this ${component?.let { "component/" } ?: ""}function.",
                ) }
                generics[it.first] = Type.Generic(it.first, it.second?.let { analyzer.visit(it).type } ?: Type.ANY)
            }
            generics.forEach { context.scope.types.define(it.key, it.value) }
            val parameters = ast.parameters.mapIndexed { index, parameter ->
                val type = parameter.second?.let { analyzer.visit(it).type } ?: component?.type?.takeIf { index == 0 } ?: throw analyzer.error(ast,
                    "Missing parameter type.",
                    "The function ${ast.name}/${ast.parameters.size} requires parameter ${index} to have an explicit type.",
                )
                Variable.Declaration(parameter.first, type)
            }
            val returns = ast.returns?.let { analyzer.visit(it).type } ?: Type.VOID
            val throws = ast.throws.map { analyzer.visit(it).type }
            val declaration = Function.Declaration(ast.name, modifiers ?: Modifiers(), generics, parameters, returns, throws)
            analyzer.require(scope.functions[ast.name, ast.parameters.size, true].all { it.isDisjointWith(declaration) }) { analyzer.error(ast,
                "Redefined function.",
                "The function ${ast.name}/${ast.parameters.size} overlaps with an existing function in ${component?.name ?: "this scope"}.",
            ) }
            val inherited = component?.inherited?.functions?.get(declaration.name, declaration.parameters.map { it.type })
            analyzer.require(inherited == null || inherited.modifiers.inheritance in listOf(Modifiers.Inheritance.VIRTUAL, Modifiers.Inheritance.ABSTRACT)) { analyzer.error(ast,
                "Invalid override.",
                "The function ${ast.name}/${ast.parameters.size} overrides an inherited function that is final.",
            ) }
            analyzer.require(inherited != null || !declaration.modifiers.override) { analyzer.error(ast,
                "Invalid override modifier.",
                "The function ${ast.name}/${ast.parameters.size} does not override an inherited function but has an `override` modifier.",
            ) }
            analyzer.require(inherited == null || declaration.modifiers.override) { analyzer.error(ast,
                "Missing override modifier.",
                "The function ${ast.name}/${ast.parameters.size} overrides an inherited function and must have an `override` modifier.",
            ) }
            val method = if (component != null || scope is Scope.Definition) Function.Definition(declaration) else declaration
            (scope as Scope<*, *, in Function, *>).functions.define(method)
            ast.operator?.let { scope.functions.define(method, it) }
            RhovasIr.DefinitionPhase.Function(ast, method)
        }
    }

}
