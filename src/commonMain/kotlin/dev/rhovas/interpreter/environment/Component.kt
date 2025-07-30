package dev.rhovas.interpreter.environment

import dev.rhovas.interpreter.environment.type.Bindings
import dev.rhovas.interpreter.environment.type.Type

sealed class Component<S: Scope<in Variable.Definition, out Variable, in Function.Definition, out Function>>(
    val name: String,
    val modifiers: Modifiers,
) {

    val generics = linkedMapOf<String, Type.Generic>()
    val inherits = mutableListOf<Type.Reference>()
    val type = Type.Reference(this, generics)

    val inherited = Scope.Declaration(null)
    val scope: S = when (modifiers.inheritance) {
        Modifiers.Inheritance.FINAL, Modifiers.Inheritance.VIRTUAL -> Scope.Definition(inherited as Scope<*, out Variable.Definition, *, out Function.Definition>) as S
        Modifiers.Inheritance.ABSTRACT -> Scope.Declaration(inherited) as S
    }

    open fun inherit(type: Type.Reference) {
        require(inherits.contains(type))
        when (this) {
            is Struct -> require(type.component == Type.STRUCT.component || type.component is Interface)
            is Class -> require(type.component.modifiers.inheritance in listOf(Modifiers.Inheritance.VIRTUAL, Modifiers.Inheritance.ABSTRACT))
            is Interface -> require(type.component == Type.ANY.component || type.component is Interface)
        }
        type.component.scope.functions.collect()
            .flatMap { entry -> entry.value.map { Pair(entry.key.first, it) } }
            .filter { (_, function) -> function.parameters.firstOrNull()?.type?.isSupertypeOf(type, Bindings.Subtype(mutableMapOf())) ?: false }
            .map { (name, function) -> Pair(name, function.bind(type.component.generics.keys.zip(type.generics.values).associate { it.first to it.second })) }
            .filter { (name, function) -> scope.functions[name, function.parameters.size].all { it.isDisjointWith(function) } }
            .forEach { (name, function) ->
                //require(function is Function.Definition || modifiers.inheritance == Modifiers.Inheritance.ABSTRACT)
                inherited.functions.define(function, name)
            }
    }

    override fun equals(other: Any?): Boolean {
        return other is Component<*> && name == other.name && modifiers == other.modifiers && generics == other.generics && inherits == other.inherits
    }

    override fun toString(): String {
        return "Component(name='${name}', modifiers=${modifiers}), generics=${generics}, inherits=${inherits})"
    }

    class Struct(
        name: String,
        modifiers: Modifiers = Modifiers(),
    ) : Component<Scope.Definition>(name, modifiers) {

        init {
            require(modifiers.inheritance == Modifiers.Inheritance.FINAL)
        }

    }

    class Class(
        name: String,
        modifiers: Modifiers = Modifiers(),
    ) : Component<Scope<in Variable.Definition, out Variable, in Function.Definition, out Function>>(name, modifiers)

    class Interface(
        name: String,
        modifiers: Modifiers = Modifiers(Modifiers.Inheritance.ABSTRACT),
    ) : Component<Scope.Declaration>(name, modifiers) {

        init {
            require(modifiers.inheritance == Modifiers.Inheritance.ABSTRACT)
        }

    }

}
