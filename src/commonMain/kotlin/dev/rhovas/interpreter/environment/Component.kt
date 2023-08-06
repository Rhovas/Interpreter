package dev.rhovas.interpreter.environment

sealed class Component<S: Scope<out Variable, out Function>>(
    val name: String,
    val modifiers: Modifiers,
    val scope: S,
) {

    init {
        when (modifiers.inheritance) {
            Modifiers.Inheritance.DEFAULT, Modifiers.Inheritance.VIRTUAL -> require(scope is Scope.Definition)
            Modifiers.Inheritance.ABSTRACT -> require(scope is Scope.Declaration)
        }
    }

    val generics: MutableList<Type.Generic> = mutableListOf()
    val inherits: MutableList<Type> = mutableListOf()
    val type = Type.Reference(this, generics)

    open fun inherit(type: Type.Reference) {
        when (this) {
            is Struct -> require(type.component == Type.STRUCT.GENERIC.component || type.component is Interface)
            is Class -> require(type.component.modifiers.inheritance in listOf(Modifiers.Inheritance.VIRTUAL, Modifiers.Inheritance.ABSTRACT))
            is Interface -> require(type.component == Type.ANY.component || type.component is Interface)
        }
        inherits.add(type)
        type.component.scope.functions.collect()
            .flatMap { entry -> entry.value.map { Pair(entry.key.first, it) } }
            .filter { (_, function) -> function.parameters.firstOrNull()?.type?.isSupertypeOf(type) ?: false }
            .map { (name, function) -> Pair(name, function.bind(type.component.generics.zip(type.generics).associate { it.first.name to it.second })) }
            .filter { (name, function) -> scope.functions[name, function.parameters.size].all { it.isDisjointWith(function) } }
            .forEach { (name, function) ->
                require(function is Function.Definition || modifiers.inheritance == Modifiers.Inheritance.ABSTRACT)
                (scope as Scope<*, Function>).functions.define(function, name)
            }
    }

    override fun equals(other: Any?): Boolean {
        return other is Component<*> && name == other.name && modifiers == other.modifiers && generics == other.generics && inherits == other.inherits
    }

    override fun toString(): String {
        return "Component(name='${name}', modifiers=${modifiers}, generics=${generics}, inherits=${inherits})"
    }

    class Struct(
        name: String,
        modifiers: Modifiers,
        scope: Scope.Definition,
    ) : Component<Scope.Definition>(name, modifiers, scope)

    class Class(
        name: String,
        modifiers: Modifiers,
        scope: Scope<out Variable, out Function>,
    ) : Component<Scope<out Variable, out Function>>(name, modifiers, scope)

    class Interface(
        name: String,
        modifiers: Modifiers,
        scope: Scope.Declaration,
    ) : Component<Scope.Declaration>(name, modifiers, scope)

}
