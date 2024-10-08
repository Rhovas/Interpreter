package dev.rhovas.interpreter.analyzer.rhovas

import com.ionspin.kotlin.bignum.decimal.BigDecimal
import com.ionspin.kotlin.bignum.integer.BigInteger
import dev.rhovas.interpreter.analyzer.AnalyzeException
import dev.rhovas.interpreter.analyzer.Analyzer
import dev.rhovas.interpreter.environment.Component
import dev.rhovas.interpreter.environment.Function
import dev.rhovas.interpreter.environment.Method
import dev.rhovas.interpreter.environment.Modifiers
import dev.rhovas.interpreter.environment.Scope
import dev.rhovas.interpreter.environment.type.Type
import dev.rhovas.interpreter.environment.Variable
import dev.rhovas.interpreter.library.Library
import dev.rhovas.interpreter.parser.Input
import dev.rhovas.interpreter.parser.rhovas.RhovasAst

class RhovasAnalyzer(scope: Scope<in Variable.Definition, out Variable, in Function.Definition, out Function>) :
    Analyzer(Context(listOf(
        InputContext(ArrayDeque()),
        ScopeContext(scope),
        InferenceContext(Type.ANY),
        InitializationContext(mutableMapOf()),
        FunctionContext(null),
        LabelContext(mutableSetOf()),
        JumpContext(mutableSetOf()),
        ExceptionContext(mutableSetOf(Type.EXCEPTION)),
    ).associateBy { it::class.simpleName!! })),
    RhovasIr.DefinitionPhase.Visitor<RhovasIr> {

    private val register = RegistrationPhase(this)
    private val declare = DeclarationPhase(this)
    private val define = DefinitionPhase(this)

    private val Context.scope get() = this[ScopeContext::class]
    private val Context.inference get() = this[InferenceContext::class]
    private val Context.initialization get() = this[InitializationContext::class]
    private val Context.component get() = this[ComponentContext::class]
    private val Context.function get() = this[FunctionContext::class]
    private val Context.labels get() = this[LabelContext::class]
    private val Context.jumps get() = this[JumpContext::class]
    private val Context.exceptions get() = this[ExceptionContext::class]
    private val Context.bindings get() = this[BindingContext::class]

    private fun Context.forComponent(component: Component<*>) = child().with(
        ComponentContext(component.type, component.inherits.firstOrNull()?.takeIf { component is Component.Class && it != Type.ANY }),
        InferenceContext(Type.ANY),
        InitializationContext(mutableMapOf()),
        FunctionContext(null),
        LabelContext(mutableSetOf()),
        JumpContext(mutableSetOf()),
        ExceptionContext(mutableSetOf()),
    )

    private fun Context.forFunction(function: Function) = child().with(
        FunctionContext(function),
        ExceptionContext(function.throws.toMutableSet()),
        InferenceContext(Type.ANY),
        InitializationContext(mutableMapOf()),
        LabelContext(mutableSetOf()),
        JumpContext(mutableSetOf()),
    )

    /**
     * Context for variable/function/type scope.
     */
    data class ScopeContext(
        val scope: Scope<in Variable.Definition, out Variable, in Function.Definition, out Function>,
    ) : Context.Item<Scope<in Variable.Definition, out Variable, in Function.Definition, out Function>>(scope) {

        override fun child(): ScopeContext {
            return ScopeContext(Scope.Declaration(scope))
        }

        override fun merge(children: List<Scope<in Variable.Definition, out Variable, in Function.Definition, out Function>>) {}

    }

    /**
     * Context for storing the expected type for type inference.
     */
    data class InferenceContext(
        val type: Type,
    ) : Context.Item<Type>(type) {

        override fun child(): InferenceContext {
            return this
        }

        override fun merge(children: List<Type>) {}

    }

    /**
     * Context for tracking initialized/uninitialized variables.
     */
    data class InitializationContext(
        val variables: MutableMap<String, Data>,
    ) : Context.Item<MutableMap<String, InitializationContext.Data>>(variables) {

        data class Data(
            var initialized: Boolean,
            val declaration: RhovasAst.Statement.Declaration.Variable,
            val context: MutableList<RhovasAst.Statement.Assignment>,
        )

        override fun child(): InitializationContext {
            return InitializationContext(variables.mapValues { it.value.copy() }.toMutableMap())
        }

        /**
         * Merges by updating any uninitialized variables that have become
         * initialized. If only some child contexts have initialized the
         * variable, the initialization is invalid and an error is thrown.
         */
        override fun merge(children: List<MutableMap<String, Data>>) {
            variables.filter { !it.value.initialized }.forEach { (variable, data) ->
                when (children.count { it[variable]!!.initialized }) {
                    0 -> {} //not initialized
                    children.size -> {
                        data.initialized = true
                        data.context.addAll(children.flatMap { it[variable]!!.context })
                    }
                    else -> throw AnalyzeException(
                        "Invalid initialization.",
                        "The variable ${variable} is being partially initialized across branches.",
                        data.declaration.context.firstOrNull() ?: Input.Range(0, 1, 0, 0),
                        (data.context + children.flatMap { it[variable]!!.context }).mapNotNull { it.context.firstOrNull() },
                    )
                }
            }
        }

    }

    /**
     * Context for storing the surrounding component declaration.
     */
    data class ComponentContext(
        val type: Type,
        val extends: Type?,
    ) : Context.Item<ComponentContext.Data>(Data(type, extends)) {

        data class Data(
            val type: Type,
            val extends: Type?,
        )

        override fun child(): ComponentContext {
            return this
        }

        override fun merge(children: List<Data>) {}

    }

    /**
     * Context for storing the surrounding function declaration.
     */
    data class FunctionContext(
        val function: Function?
    ) : Context.Item<Function?>(function) {

        override fun child(): FunctionContext {
            return this
        }

        override fun merge(children: List<Function?>) {}

    }

    /**
     * Context for storing the defined labels. The `null` label represents an
     * unlabeled loop that can be used by break/continue.
     */
    data class LabelContext(
        val labels: MutableSet<String?>,
    ) : Context.Item<MutableSet<String?>>(labels) {

        override fun child(): LabelContext {
            return LabelContext(labels.toMutableSet())
        }

        override fun merge(children: List<MutableSet<String?>>) {}

    }

    /**
     * Context for tracking the potential jump targets.
     *
     *  - The `null` target represents an unlabeled loop that can be used by
     *    `break`/`continue`, as with `LabelContext`.
     *  - The `""` target represents a top-level `return`/`throw`.
     */
    data class JumpContext(
        val jumps: MutableSet<String?>,
    ) : Context.Item<MutableSet<String?>>(jumps) {

        override fun child(): Context.Item<MutableSet<String?>> {
            return JumpContext(mutableSetOf())
        }

        /**
         * Merges by adding all potential jump targets to this context if all
         * branches resulted in a jump (`isNotEmpty`).
         */
        override fun merge(children: List<MutableSet<String?>>) {
            if (children.all { it.isNotEmpty() }) {
                jumps.addAll(children.flatten())
            }
        }

    }

    /**
     * Context for storing the caught exception types.
     */
    data class ExceptionContext(
        val exceptions: MutableSet<Type>,
    ) : Context.Item<MutableSet<Type>>(exceptions) {

        override fun child(): Context.Item<MutableSet<Type>> {
            return ExceptionContext(exceptions.toMutableSet())
        }

        override fun merge(children: List<MutableSet<Type>>) {}

    }

    /**
     * Context for storing the variable bindings for patterns.
     */
    data class BindingContext(
        val bindings: MutableMap<String, Variable.Declaration>,
    ) : Context.Item<MutableMap<String, Variable.Declaration>>(bindings) {

        override fun child(): BindingContext {
            return this
        }

        override fun merge(children: List<MutableMap<String, Variable.Declaration>>) {}

    }

    override fun visit(ast: RhovasAst.Source): RhovasIr.Source = analyzeAst(ast) {
        val imports = ast.imports.map { visit(it) }
        // Not elegant, but holdover until analyzer phase refactor
        register.visit(ast)
        val statements = ast.statements.map {
            if (it is RhovasAst.Statement.Component) {
                declare.visit(it.component)
                it.component
            } else it
        }.map {
            when (it) {
                is RhovasAst.Component.Struct -> define.visit(it)
                is RhovasAst.Component.Class -> define.visit(it)
                is RhovasAst.Component.Interface -> define.visit(it)
                is RhovasAst.Statement.Declaration.Function -> define.visit(it)
                else -> it
            }
        }.map {
            when (it) {
                is RhovasIr.DefinitionPhase.Component.Struct -> RhovasIr.Statement.Component(visit(it))
                is RhovasIr.DefinitionPhase.Component.Class -> RhovasIr.Statement.Component(visit(it))
                is RhovasIr.DefinitionPhase.Component.Interface -> RhovasIr.Statement.Component(visit(it))
                is RhovasIr.DefinitionPhase.Function -> visit(it)
                else -> visit(it as RhovasAst.Statement)
            }
        }
        RhovasIr.Source(imports, statements)
    }

    override fun visit(ast: RhovasAst.Import): RhovasIr.Import = analyzeAst(ast) {
        val type = Library.TYPES[ast.path.joinToString(".")] ?: throw error(ast,
            "Undefined type.",
            "The type ${ast.path.joinToString(".")} is not defined."
        )
        val alias = ast.alias ?: ast.path.last()
        require(!context.scope.types.isDefined(alias, true)) { error(ast,
            "Redefined type.",
            "The type ${ast.path.last()} is already defined in the current scope.",
        ) }
        context.scope.types.define(alias, type)
        RhovasIr.Import(type)
    }

    private fun visit(ast: RhovasAst.Component): RhovasIr.Component {
        return super.visit(ast) as RhovasIr.Component
    }

    override fun visit(ast: RhovasAst.Component.Struct): RhovasIr.Component.Struct {
        register.visit(ast)
        declare.visit(ast)
        val ir = define.visit(ast) as RhovasIr.DefinitionPhase.Component.Struct
        return visit(ir)
    }

    override fun visit(ir: RhovasIr.DefinitionPhase.Component.Struct): RhovasIr.Component.Struct = analyzeAst(ir.ast) {
        analyze(context.forComponent(ir.component)) {
            val members = ir.members.map { visit(it) }
            RhovasIr.Component.Struct(ir.component, members)
        }
    }

    override fun visit(ast: RhovasAst.Component.Class): RhovasIr.Component.Class {
        register.visit(ast)
        declare.visit(ast)
        val ir = define.visit(ast) as RhovasIr.DefinitionPhase.Component.Class
        return visit(ir)
    }

    override fun visit(ir: RhovasIr.DefinitionPhase.Component.Class): RhovasIr.Component.Class = analyzeAst(ir.ast) {
        analyze(context.forComponent(ir.component)) {
            val members = ir.members.map { visit(it) }
            RhovasIr.Component.Class(ir.component, members)
        }
    }

    override fun visit(ast: RhovasAst.Component.Interface): RhovasIr.Component.Interface {
        register.visit(ast)
        declare.visit(ast)
        val ir = define.visit(ast) as RhovasIr.DefinitionPhase.Component.Interface
        return visit(ir)
    }

    override fun visit(ir: RhovasIr.DefinitionPhase.Component.Interface): RhovasIr.Component.Interface = analyzeAst(ir.ast) {
        analyze(context.forComponent(ir.component)) {
            ir.component.generics.forEach { context.scope.types.define(it.key, it.value) }
            val members = ir.members.map { visit(it) }
            RhovasIr.Component.Interface(ir.component, members)
        }
    }

    private fun visit(ir: RhovasIr.DefinitionPhase.Member): RhovasIr.Member {
        return super.visit(ir) as RhovasIr.Member
    }

    override fun visit(ir: RhovasIr.DefinitionPhase.Member.Property): RhovasIr.Member.Property = analyzeAst(ir.ast) { analyze {
        ir.getter.generics.forEach { context.scope.types.define(it.key, it.value) }
        val value = ir.ast.value?.let { visit(it, ir.getter.returns) }
        require(value == null || value.type.isSubtypeOf(ir.getter.returns)) { error(ir.ast,
            "Invalid value type.",
            "The property ${ir.ast.name} requires a value of type ${ir.getter.returns}, but received ${value!!.type}."
        ) }
        RhovasIr.Member.Property(ir.getter, ir.setter, value)
    } }

    override fun visit(ir: RhovasIr.DefinitionPhase.Member.Initializer): RhovasIr.Member.Initializer = analyzeAst(ir.ast) {
        analyze(context.forFunction(ir.function)) {
            ir.function.generics.forEach { context.scope.types.define(it.key, it.value) }
            (context.scope as Scope.Declaration).variables.define(Variable.Declaration("this", ir.function.returns))
            context.initialization["this"] = InitializationContext.Data(false, RhovasAst.Statement.Declaration.Variable(false, "this", null, null).also { it.context = ir.ast.context }, mutableListOf())
            ir.function.parameters.forEach { (context.scope as Scope.Declaration).variables.define(it) }
            val block = visit(ir.ast.block)
            RhovasIr.Member.Initializer(ir.function, block)
        }
    }

    override fun visit(ir: RhovasIr.DefinitionPhase.Member.Method): RhovasIr.Member.Method = analyzeAst(ir.ast) {
        RhovasIr.Member.Method(visit(ir.function))
    }

    private fun visit(ast: RhovasAst.Statement): RhovasIr.Statement {
        return super.visit(ast) as RhovasIr.Statement
    }

    override fun visit(ast: RhovasAst.Statement.Component): RhovasIr.Statement.Component = analyzeAst(ast) {
        RhovasIr.Statement.Component(visit(ast.component))
    }

    override fun visit(ast: RhovasAst.Statement.Initializer): RhovasIr.Statement.Initializer = analyzeAst(ast) {
        require(context.function?.name == "") { error(ast,
            "Invalid initializer.",
            "An instance initializer can only be called within an initializer function.",
        ) }
        val initialization = context.initialization["this"]!!
        require(!initialization.initialized) { analyze(initialization.declaration.context) { error(ast,
            "Reinitialized instance.",
            "An instance initializer can only be called once.",
        ) } }
        initialization.initialized = true
        val arguments = mutableListOf<RhovasIr.Expression>()
        val delegate = when (ast.name) {
            "this" -> {
                if (ast.arguments.isNotEmpty()) {
                    resolveFunction("initializer", ast, context.component.type, "", false, ast.arguments.size) {
                        ast.arguments[it.first] to visit(ast.arguments[it.first], it.second).also { arguments.add(it) }.type
                    } as Function.Definition
                } else if (context.component.extends != null) {
                    resolveFunction("initializer", ast, context.component.extends, "", false, 0) {
                        throw AssertionError()
                    } as Function.Definition
                } else null
            }
            "super" -> {
                require(context.component.extends != null) { error(ast,
                    "Invalid super initializer.",
                    "A super initializer requires the surrounding component to extend a class.",
                ) }
                resolveFunction("initializer", ast, context.component.extends, "", false, ast.arguments.size) {
                    ast.arguments[it.first] to visit(ast.arguments[it.first], it.second).also { arguments.add(it) }.type
                } as Function.Definition
            }
            else -> throw AssertionError()
        }
        val initializer = ast.initializer?.let { visit(it, Type.STRUCT.GENERIC) as RhovasIr.Expression.Literal.Object }
        //TODO(#14): Validate available fields
        RhovasIr.Statement.Initializer(ast.name, delegate, arguments, initializer)
    }

    override fun visit(ast: RhovasAst.Statement.Expression): RhovasIr.Statement.Expression = analyzeAst(ast) {
        require(ast.expression is RhovasAst.Expression.Block || ast.expression is RhovasAst.Expression.Invoke) { error(ast.expression,
            "Invalid expression statement.",
            "An expression statement requires an invoke expression in order to perform a useful side-effect, but received ${ast.expression::class.simpleName}.",
        ) }
        val expression = visit(ast.expression)
        RhovasIr.Statement.Expression(expression)
    }

    override fun visit(ast: RhovasAst.Statement.Declaration.Variable): RhovasIr.Statement.Declaration.Variable = analyzeAst(ast) {
        require(context.scope.variables[ast.name, true] == null) { error(ast,
            "Redefined variable.",
            "The variable ${ast.name} is already defined in this scope.",
        ) }
        require(ast.type != null || ast.value != null) { error(ast,
            "Undefined variable type.",
            "A variable declaration requires either a type or an initial value.",
        ) }
        val type = ast.type?.let { visit(it).type }
        val value = ast.value?.let { visit(it, type) }
        require(type == null || value == null || value.type.isSubtypeOf(type)) { error(ast,
            "Invalid value type.",
            "The variable ${ast.name} requires a value of type ${type}, but received ${value!!.type}."
        ) }
        val variable = Variable.Declaration(ast.name, type ?: value!!.type, ast.mutable).let {
            when (val scope = context.scope) {
                is Scope.Declaration -> it.also { scope.variables.define(it) }
                is Scope.Definition -> Variable.Definition(it).also { scope.variables.define(it) }
            }
        }
        if (value == null) {
            context.initialization[variable.name] = InitializationContext.Data(false, ast, mutableListOf())
        }
        RhovasIr.Statement.Declaration.Variable(variable, value)
    }

    override fun visit(ast: RhovasAst.Statement.Declaration.Function): RhovasIr.Statement.Declaration.Function {
        return define.visit(ast).let { visit(it) }
    }

    override fun visit(ir: RhovasIr.DefinitionPhase.Function): RhovasIr.Statement.Declaration.Function = analyzeAst(ir.ast) {
        analyze(context.forFunction(ir.function)) {
            ir.function.generics.forEach { context.scope.types.define(it.key, it.value) }
            ir.function.parameters.forEach { (context.scope as Scope.Declaration).variables.define(it) }
            val block = visit(ir.ast.block)
            require(ir.function.returns.isSubtypeOf(Type.VOID) || context.jumps.contains("")) { error(ir.ast,
                "Missing return value.",
                "The function ${ir.ast.name}/${ir.ast.parameters.size} requires a return value.",
            ) }
            context.jumps.clear()
            RhovasIr.Statement.Declaration.Function(ir.function, block)
        }
    }

    override fun visit(ast: RhovasAst.Statement.Assignment): RhovasIr.Statement.Assignment = analyzeAst(ast) {
        when (ast.receiver) {
            is RhovasAst.Expression.Access.Variable -> {
                val initialization = context.initialization[ast.receiver.name]
                val receiver = when {
                    initialization == null -> visit(ast.receiver).also {
                        require(it.variable.mutable) { error(ast.receiver,
                            "Unassignable variable.",
                            "The variable ${it.variable.name} is not assignable.",
                        ) }
                    }
                    initialization.initialized -> visit(ast.receiver).also {
                        require(it.variable.mutable) { error(ast.receiver,
                            "Reinitialized variable.",
                            "The variable ${it.variable.name} is already initialized.",
                        ) }
                    }
                    else -> {
                        initialization.initialized = true
                        initialization.context.add(ast)
                        visit(ast.receiver)
                    }
                }
                val value = visit(ast.value, receiver.variable.type)
                require(value.type.isSubtypeOf(receiver.variable.type)) { error(ast.value,
                    "Invalid assignment value type.",
                    "The variable ${receiver.variable.name} requires the value to be type ${receiver.variable.type}, but received ${value.type}.",
                ) }
                RhovasIr.Statement.Assignment.Variable(receiver.variable, value)
            }
            is RhovasAst.Expression.Access.Property -> {
                require(!ast.receiver.coalesce) { error(ast.receiver,
                    "Invalid assignment receiver.",
                    "An assignment statement requires the receiver property access to be non-coalescing.",
                ) }
                val receiver = visit(ast.receiver)
                require(receiver.property.mutable) { error(ast.receiver,
                    "Unassignable property.",
                    "The property ${receiver.type}.${receiver.property.name} is not assignable.",
                ) }
                val value = visit(ast.value, receiver.property.type)
                require(value.type.isSubtypeOf(receiver.property.type)) { error(ast.value,
                    "Invalid assignment value type.",
                    "The property ${receiver.type}.${receiver.property.name} requires the value to be type ${receiver.property.type}, but received ${value.type}.",
                ) }
                RhovasIr.Statement.Assignment.Property(receiver.receiver, receiver.property, value)
            }
            is RhovasAst.Expression.Access.Index -> {
                val receiver = visit(ast.receiver.receiver)
                require(!ast.receiver.coalesce) { error(ast.receiver,
                    "Invalid coalesce.",
                    "Coalescing is not allowed within an index assignment.",
                ) }
                val (method, arguments) = resolveMethod(ast.receiver, ast.receiver.receiver, receiver.type, "[]=", false, ast.receiver.arguments + listOf(ast.value))
                RhovasIr.Statement.Assignment.Index(receiver, method, arguments.dropLast(1), arguments.last())
            }
            else -> throw error(ast.receiver,
                "Invalid assignment receiver.",
                "An assignment statement requires the receiver to be an access expression, but received ${ast.receiver::class.simpleName}.",
            )
        }
    }

    override fun visit(ast: RhovasAst.Statement.If): RhovasIr.Statement.If = analyzeAst(ast) {
        val condition = visit(ast.condition, Type.BOOLEAN)
        require(condition.type.isSubtypeOf(Type.BOOLEAN)) { error(ast.condition,
            "Invalid if condition type.",
            "An if statement requires the condition to be type Boolean, but received ${condition.type}.",
        ) }
        val thenBlock = analyze(context.child()) {
            visit(ast.thenBlock)
        }
        val elseBlock = analyze(context.child()) {
            ast.elseBlock?.let { visit(it) }
        }
        context.merge()
        RhovasIr.Statement.If(condition, thenBlock, elseBlock)
    }

    override fun visit(ast: RhovasAst.Statement.Match.Conditional): RhovasIr.Statement.Match.Conditional = analyzeAst(ast) {
        fun visitCondition(ast: RhovasAst.Expression): RhovasIr.Expression {
            val condition = visit(ast, Type.BOOLEAN)
            require(condition.type.isSubtypeOf(Type.BOOLEAN)) { error(ast,
                "Invalid match condition type.",
                "A conditional match statement requires the condition to be type Boolean, but received ${condition.type}.",
            ) }
            return condition
        }
        val cases = ast.cases.map {
            analyze(context.child()) { analyze(it.first.context) {
                val condition = visitCondition(it.first)
                val statement = visit(it.second)
                Pair(condition, statement)
            } }
        }
        val elseCase = analyze(context.child()) {
            ast.elseCase?.let { analyze((it.first ?: it.second).context) {
                val condition = it.first?.let { visitCondition(it) }
                val statement = visit(it.second)
                Pair(condition, statement)
            } }
        }
        context.merge()
        RhovasIr.Statement.Match.Conditional(cases, elseCase)
    }

    override fun visit(ast: RhovasAst.Statement.Match.Structural): RhovasIr.Statement.Match.Structural = analyzeAst(ast) {
        val argument = visit(ast.argument)
        //TODO(#10): Pattern coverage/exhaustiveness
        val cases = ast.cases.map {
            analyze(context.child().with(BindingContext(mutableMapOf()))) { analyze(it.first.context) {
                val pattern = visit(it.first, argument.type)
                context.bindings.forEach { (context.scope as Scope.Declaration).variables.define(it.value) }
                val statement = visit(it.second)
                Pair(pattern, statement)
            } }
        }
        val elseCase = ast.elseCase?.let {
            analyze(context.child().with(BindingContext(mutableMapOf()))) { analyze((it.first ?: it.second).context) {
                val pattern = it.first?.let { visit(it, argument.type) }
                context.bindings.forEach { (context.scope as Scope.Declaration).variables.define(it.value) }
                val statement = visit(it.second)
                Pair(pattern, statement)
            } }
        }
        context.merge()
        RhovasIr.Statement.Match.Structural(argument, cases, elseCase)
    }

    override fun visit(ast: RhovasAst.Statement.For): RhovasIr.Statement.For = analyzeAst(ast) {
        val argument = visit(ast.argument, Type.LIST.GENERIC)
        require(argument.type.isSubtypeOf(Type.ITERABLE.DYNAMIC)) { error(ast.argument,
            "Invalid for loop argument type.",
            "A for loop requires the argument to be type Iterable, but received ${argument.type}.",
        ) }
        val type = argument.type.generic("T", Type.ITERABLE.GENERIC)!!
        val variable = Variable.Declaration(ast.name, type)
        val block = analyze {
            (context.scope as Scope.Declaration).variables.define(variable)
            context.labels.add(null)
            visit(ast.block).also { context.jumps.clear() }
        }
        RhovasIr.Statement.For(variable, argument, block)
    }

    override fun visit(ast: RhovasAst.Statement.While): RhovasIr.Statement.While = analyzeAst(ast) {
        val condition = visit(ast.condition, Type.BOOLEAN)
        require(condition.type.isSubtypeOf(Type.BOOLEAN)) { error(ast.condition,
            "Invalid while condition type.",
            "An while statement requires the condition to be type Boolean, but received ${condition.type}.",
        ) }
        val block = analyze {
            context.labels.add(null)
            visit(ast.block).also { context.jumps.clear() }
        }
        RhovasIr.Statement.While(condition, block)
    }

    override fun visit(ast: RhovasAst.Statement.Try): RhovasIr.Statement.Try = analyzeAst(ast) {
        val child = context.child()
        ast.catchBlocks.forEach { analyze(it.context) {
            val type = visit(it.type).type
            require(type.isSubtypeOf(Type.EXCEPTION)) { error(it.type,
                "Invalid catch type",
                "An catch block requires the type to be a subtype of Exception, but received ${type}."
            ) }
            child.exceptions.add(type)
        } }
        val tryBlock = analyze(child) { visit(ast.tryBlock) }
        val catchBlocks = ast.catchBlocks.map { analyzeAst(it) {
            val type = visit(it.type).type //validated as subtype of Exception above
            val variable = Variable.Declaration(it.name, type)
            val block = analyze(context.child()) {
                (context.scope as Scope.Declaration).variables.define(variable)
                visit(it.block)
            }
            RhovasIr.Statement.Try.Catch(variable, block)
        } }
        context.merge()
        val finallyBlock = ast.finallyBlock?.let {
            analyze(context.with(ExceptionContext(mutableSetOf()))) {
                visit(it)
            }
        }
        RhovasIr.Statement.Try(tryBlock, catchBlocks, finallyBlock)
    }

    override fun visit(ast: RhovasAst.Statement.Try.Catch): RhovasIr.Statement.Try.Catch {
        throw AssertionError()
    }

    override fun visit(ast: RhovasAst.Statement.With): RhovasIr.Statement.With = analyzeAst(ast) {
        val argument = visit(ast.argument)
        val variable = ast.name?.let { Variable.Declaration(ast.name, argument.type) }
        val block = analyze {
            variable?.let { (context.scope as Scope.Declaration).variables.define(it) }
            visit(ast.block)
        }
        RhovasIr.Statement.With(variable, argument, block)
    }

    override fun visit(ast: RhovasAst.Statement.Label): RhovasIr.Statement.Label = analyzeAst(ast) {
        require(ast.statement is RhovasAst.Statement.For || ast.statement is RhovasAst.Statement.While) { error(ast.statement,
            "Invalid label statement.",
            "A label statement requires the statement to be a for/while loop.",
        ) }
        require(!context.labels.contains(ast.label)) { error(ast,
            "Redefined label.",
            "The label ${ast.label} is already defined in this scope.",
        ) }
        val statement = analyze {
            context.labels.add(ast.label)
            visit(ast.statement).also { context.jumps.remove(ast.label) }
        }
        RhovasIr.Statement.Label(ast.label, statement)
    }

    override fun visit(ast: RhovasAst.Statement.Break): RhovasIr.Statement.Break = analyzeAst(ast) {
        require(context.labels.contains(null)) { error(ast,
            "Invalid break statement.",
            "A break statement requires an enclosing for/while loop.",
        ) }
        require(context.labels.contains(ast.label)) { error(ast,
            "Undefined label.",
            "The label ${ast.label} is not defined in this scope.",
        ) }
        context.jumps.add(ast.label)
        RhovasIr.Statement.Break(ast.label)
    }

    override fun visit(ast: RhovasAst.Statement.Continue): RhovasIr.Statement.Continue = analyzeAst(ast) {
        require(context.labels.contains(null)) { error(ast,
            "Invalid continue statement.",
            "A continue statement requires an enclosing for/while loop.",
        ) }
        require(context.labels.contains(ast.label)) { error(ast,
            "Undefined label.",
            "The label ${ast.label} is not defined in this scope.",
        ) }
        context.jumps.add(ast.label)
        RhovasIr.Statement.Continue(ast.label)
    }

    override fun visit(ast: RhovasAst.Statement.Return): RhovasIr.Statement.Return = analyzeAst(ast) {
        require(context.function != null) { error(ast,
            "Invalid return statement.",
            "A return statement requires an enclosing function definition.",
        ) }
        val value = ast.value?.let { visit(it, context.function!!.returns) }
        require((value?.type ?: Type.VOID).isSubtypeOf(context.function!!.returns)) { error(ast,
            "Invalid return value type.",
            "The enclosing function ${context.function!!.name}/${context.function!!.parameters.size} requires the return value to be type ${context.function!!.returns}, but received ${value?.type ?: Type.VOID}.",
        ) }
        context.jumps.add("")
        RhovasIr.Statement.Return(value, listOf())
    }

    override fun visit(ast: RhovasAst.Statement.Throw): RhovasIr.Statement.Throw = analyzeAst(ast) {
        val exception = visit(ast.exception, Type.EXCEPTION)
        require(exception.type.isSubtypeOf(Type.EXCEPTION)) { error(ast.exception,
            "Invalid throw expression type.",
            "An throw statement requires the expression to be type Exception, but received ${exception.type}.",
        ) }
        require(context.exceptions.any { exception.type.isSubtypeOf(it) }) { error(ast.exception,
            "Uncaught exception.",
            "An exception is thrown of type ${exception.type}, but this exception is never caught or declared.",
        ) }
        context.jumps.add("")
        RhovasIr.Statement.Throw(exception)
    }

    override fun visit(ast: RhovasAst.Statement.Assert): RhovasIr.Statement.Assert = analyzeAst(ast) {
        val condition = visit(ast.condition, Type.BOOLEAN)
        require(condition.type.isSubtypeOf(Type.BOOLEAN)) { error(ast.condition,
            "Invalid assert condition type.",
            "An assert statement requires the condition to be type Boolean, but received ${condition.type}.",
        ) }
        val message = ast.message?.let { visit(it, Type.STRING) }
        require(message == null || message.type.isSubtypeOf(Type.STRING)) { error(ast.message!!,
            "Invalid assert message type.",
            "An assert statement requires the message to be type String, but received ${message!!.type}.",
        ) }
        RhovasIr.Statement.Assert(condition, message)
    }

    override fun visit(ast: RhovasAst.Statement.Require): RhovasIr.Statement.Require = analyzeAst(ast) {
        val condition = visit(ast.condition, Type.BOOLEAN)
        require(condition.type.isSubtypeOf(Type.BOOLEAN)) { error(ast.condition,
            "Invalid require condition type.",
            "A require statement requires the condition to be type Boolean, but received ${condition.type}.",
        ) }
        val message = ast.message?.let { visit(it, Type.STRING) }
        require(message == null || message.type.isSubtypeOf(Type.STRING)) { error(ast.message!!,
            "Invalid require message type.",
            "A require statement requires the message to be type String, but received ${message!!.type}.",
        ) }
        RhovasIr.Statement.Require(condition, message)
    }

    override fun visit(ast: RhovasAst.Statement.Ensure): RhovasIr.Statement.Ensure = analyzeAst(ast) {
        val condition = visit(ast.condition, Type.BOOLEAN)
        require(condition.type.isSubtypeOf(Type.BOOLEAN)) { error(ast.condition,
            "Invalid ensure condition type.",
            "An ensure statement requires the condition to be type Boolean, but received ${condition.type}.",
        ) }
        val message = ast.message?.let { visit(it, Type.STRING) }
        require(message == null || message.type.isSubtypeOf(Type.STRING)) { error(ast.message!!,
            "Invalid ensure message type.",
            "An ensure statement requires the message to be type String, but received ${message!!.type}.",
        ) }
        RhovasIr.Statement.Ensure(condition, message)
    }

    private fun visit(ast: RhovasAst.Expression, type: Type? = null): RhovasIr.Expression {
        return analyze(context.with(InferenceContext(type ?: Type.ANY))) { super.visit(ast) as RhovasIr.Expression }
    }

    override fun visit(ast: RhovasAst.Expression.Block): RhovasIr.Expression.Block = analyzeAst(ast) { analyze {
        val statements = mutableListOf<RhovasIr.Statement>()
        ast.statements.forEach {
            val previous = statements.lastOrNull()
            if (previous is RhovasIr.Statement.Return && it is RhovasAst.Statement.Ensure) {
                val ensure = analyze {
                    previous.value?.let { (context.scope as Scope.Declaration).variables.define(Variable.Declaration("val", it.type)) }
                    visit(it)
                }
                statements[statements.lastIndex] = RhovasIr.Statement.Return(previous.value, listOf(ensure)).also { it.context = previous.context }
            } else {
                require(context.jumps.isEmpty()) { error(it,
                    "Unreachable statement.",
                    "A previous statement changes control flow to always jump past this statement.",
                ) }
                statements.add(visit(it))
            }
        }
        val expression = ast.expression?.let {
            require(context.jumps.isEmpty()) { error(it,
                "Unreachable statement.",
                "A previous statement changes control flow to always jump past this statement.",
            ) }
            visit(it, context.inference)
        }
        val type = expression?.type ?: Type.VOID
        RhovasIr.Expression.Block(statements, expression, type)
    } }

    override fun visit(ast: RhovasAst.Expression.Literal.Scalar): RhovasIr.Expression.Literal.Scalar = analyzeAst(ast) {
        val type = when (ast.value) {
            null -> Type.NULLABLE.DYNAMIC
            is Boolean -> Type.BOOLEAN
            is BigInteger -> Type.INTEGER
            is BigDecimal -> Type.DECIMAL
            is RhovasAst.Atom -> Type.ATOM
            else -> throw AssertionError()
        }
        RhovasIr.Expression.Literal.Scalar(ast.value, type)
    }

    override fun visit(ast: RhovasAst.Expression.Literal.String): RhovasIr.Expression.Literal.String = analyzeAst(ast) {
        val arguments = ast.arguments.map { visit(it, Type.STRING) }
        val type = Type.STRING
        RhovasIr.Expression.Literal.String(ast.literals, arguments, type)
    }

    override fun visit(ast: RhovasAst.Expression.Literal.List): RhovasIr.Expression.Literal.List = analyzeAst(ast) {
        val (elements, type) = if (context.inference != Type.DYNAMIC && context.inference.isSubtypeOf(Type.TUPLE.GENERIC)) {
            val generics = (context.inference.generic("T", Type.TUPLE.GENERIC)!! as? Type.Tuple)?.elements
            val elements = ast.elements.withIndex().map { visit(it.value, generics?.getOrNull(it.index)?.type) }
            val type = Type.TUPLE[elements.map { it.type }, true]
            Pair(elements, type)
        } else {
            val inference = context.inference.generic("T", Type.LIST.GENERIC) ?: Type.ANY
            val elements = ast.elements.map { visit(it, inference) }
            val type = Type.LIST[elements.map { it.type }.reduceOrNull { acc, type -> acc.unify(type) } ?: Type.DYNAMIC]
            Pair(elements, type)
        }
        RhovasIr.Expression.Literal.List(elements, type)
    }

    override fun visit(ast: RhovasAst.Expression.Literal.Object): RhovasIr.Expression.Literal.Object = analyzeAst(ast) {
        val (properties, type) = if (context.inference != Type.DYNAMIC && context.inference.isSubtypeOf(Type.MAP.GENERIC)) {
            val inferredKey = context.inference.generic("K", Type.MAP.GENERIC) ?: Type.ANY
            val inferredValue = context.inference.generic("V", Type.MAP.GENERIC) ?: Type.ANY
            val properties = mutableMapOf<String, RhovasIr.Expression>()
            ast.properties.forEach {
                require(properties[it.first] == null) { error(ast,
                    "Redefined object property.",
                    "The property ${it.first} has already been defined for this object.",
                ) }
                properties[it.first] = visit(it.second, inferredValue)
            }
            val keyType = inferredKey.takeIf { properties.isEmpty() || !it.isSupertypeOf(Type.ATOM) } ?: Type.ATOM
            val valueType = properties.values.map { it.type }.reduceOrNull { acc, type -> acc.unify(type) } ?: Type.DYNAMIC
            Pair(properties, Type.MAP[keyType, valueType])
        } else {
            val inference = (context.inference.generic("T", Type.STRUCT.GENERIC) as? Type.Struct)?.fields
            val properties = mutableMapOf<String, RhovasIr.Expression>()
            ast.properties.forEach {
                require(properties[it.first] == null) { error(ast,
                    "Redefined object property.",
                    "The property ${it.first} has already been defined for this object.",
                ) }
                properties[it.first] = visit(it.second, inference?.get(it.first)?.type)
            }
            val type = Type.STRUCT[properties.map { it.key to it.value.type }, true]
            Pair(properties, type)
        }
        RhovasIr.Expression.Literal.Object(properties, type)
    }

    override fun visit(ast: RhovasAst.Expression.Literal.Type): RhovasIr.Expression.Literal.Type = analyzeAst(ast) {
        val type = visit(ast.type).type
        val expressionType = Type.TYPE[type]
        RhovasIr.Expression.Literal.Type(type, expressionType)
    }

    override fun visit(ast: RhovasAst.Expression.Group): RhovasIr.Expression.Group = analyzeAst(ast) {
        val expression = visit(ast.expression, context.inference)
        RhovasIr.Expression.Group(expression)
    }

    override fun visit(ast: RhovasAst.Expression.Unary): RhovasIr.Expression.Unary = analyzeAst(ast) {
        val expression = visit(ast.expression)
        val (method, _) = resolveMethod(ast, ast.expression, expression.type, ast.operator, false, listOf())
        RhovasIr.Expression.Unary(ast.operator, expression, method)
    }

    override fun visit(ast: RhovasAst.Expression.Binary): RhovasIr.Expression.Binary = analyzeAst(ast) {
        when (ast.operator) {
            "&&", "||" -> {
                val left = visit(ast.left, Type.BOOLEAN)
                require(left.type.isSubtypeOf(Type.BOOLEAN)) { error(ast.left,
                    "Invalid binary operand.",
                    "A logical binary expression requires the left operand to be type Boolean, but received ${left.type}.",
                ) }
                val right = visit(ast.right, Type.BOOLEAN)
                require(right.type.isSubtypeOf(Type.BOOLEAN)) { error(ast.right,
                    "Invalid binary operand.",
                    "A logical binary expression requires the left operand to be type Boolean, but received ${left.type}.",
                ) }
                RhovasIr.Expression.Binary(ast.operator, left, right, null, Type.BOOLEAN)
            }
            "==", "!=" -> {
                val left = visit(ast.left)
                require(left.type.isSubtypeOf(Type.EQUATABLE.DYNAMIC) || left.type.isSupertypeOf(Type.EQUATABLE.DYNAMIC)) { error(ast.left,
                    "Unequatable type.",
                    "A logical binary expression requires the left operand to be unifiable with type Equatable, but received ${left.type}.",
                ) }
                val right = visit(ast.right, left.type)
                require(right.type.isSubtypeOf(Type.EQUATABLE.DYNAMIC) || right.type.isSupertypeOf(Type.EQUATABLE.DYNAMIC)) { error(ast.right,
                    "Unequatable type.",
                    "A logical binary expression requires the right operand to be unifiable with type Equatable, but received ${right.type}.",
                ) }
                RhovasIr.Expression.Binary(ast.operator, left, right, null, Type.BOOLEAN)
            }
            "===", "!==" -> {
                val left = visit(ast.left)
                val right = visit(ast.right, left.type)
                RhovasIr.Expression.Binary(ast.operator, left, right, null, Type.BOOLEAN)
            }
            "<", ">", "<=", ">=" -> {
                val left = visit(ast.left)
                require(left.type.isSubtypeOf(Type.COMPARABLE.DYNAMIC)) { error(ast.left,
                    "Uncomparable type.",
                    "A logical equality expression requires the left operand to be type Comparable, but received ${left.type}.",
                ) }
                val (method, arguments) = resolveMethod(ast, ast.left, left.type, "<=>", false, listOf(ast.right))
                RhovasIr.Expression.Binary(ast.operator, left, arguments[0], method, Type.BOOLEAN)
            }
            "+", "-", "*", "/" -> {
                val left = visit(ast.left)
                val (method, arguments) = resolveMethod(ast, ast.left, left.type, ast.operator, false, listOf(ast.right))
                RhovasIr.Expression.Binary(ast.operator, left, arguments[0], method, method.returns)
            }
            else -> throw AssertionError()
        }
    }

    override fun visit(ast: RhovasAst.Expression.Access.Variable): RhovasIr.Expression.Access.Variable = analyzeAst(ast) {
        val qualifier = ast.qualifier?.let { visit(it) }
        val component = qualifier?.let { (it.type as? Type.Reference)?.component ?: throw error(ast,
            "Invalid variable qualifier.",
            "Qualified variables require a reference type, but received a base type of Type.${it.type::class.simpleName} (${it.type}).",
        ) }
        val variable = (component?.scope ?: context.scope).variables[ast.name] ?: throw error(ast,
            "Undefined variable.",
            "The variable ${ast.name} is not defined in ${component?.name ?: "the current scope"}."
        )
        val initialization = context.initialization[ast.name]
        require(initialization == null || initialization.initialized) { analyze(initialization!!.declaration.context) { error(ast,
            "Uninitialized variable.",
            "The variable ${ast.name} is not initialized.",
        ) } }
        RhovasIr.Expression.Access.Variable(qualifier, variable)
    }

    override fun visit(ast: RhovasAst.Expression.Access.Property): RhovasIr.Expression.Access.Property = analyzeAst(ast) {
        val receiver = visit(ast.receiver)
        val receiverType = computeCoalesceReceiver(receiver, ast.coalesce)
        val bang = ast.name.endsWith('!')
        val property = receiverType.properties[ast.name.removeSuffix("!")] ?: throw error(ast,
            "Undefined property.",
            "The property getter ${ast.name.removeSuffix("!")}() is not defined in ${receiverType}.",
        )
        val returnsType = if (bang) {
            require(property.type.isSubtypeOf(Type.RESULT.DYNAMIC)) { error(ast,
                "Invalid bang attribute.",
                "A bang attribute requires the property getter ${ast.name.removeSuffix("!")}() to return type Result, but received ${property.type}"
            ) }
            property.type.methods["value!", listOf()]!!.returns
        } else property.type
        val type = computeCoalesceCascadeReturn(returnsType, receiver.type, ast.coalesce, false)
        RhovasIr.Expression.Access.Property(receiver, property, bang, ast.coalesce, type)
    }

    override fun visit(ast: RhovasAst.Expression.Access.Index): RhovasIr.Expression.Access.Index = analyzeAst(ast) {
        val receiver = visit(ast.receiver)
        val receiverType = computeCoalesceReceiver(receiver, ast.coalesce)
        val (method, arguments) = resolveMethod(ast, ast.receiver, receiverType, "[]", false, ast.arguments)
        val type = computeCoalesceCascadeReturn(method.returns, receiver.type, ast.coalesce, false)
        RhovasIr.Expression.Access.Index(receiver, method, ast.coalesce, arguments, type)
    }

    override fun visit(ast: RhovasAst.Expression.Invoke.Constructor): RhovasIr.Expression.Invoke.Constructor = analyzeAst(ast) {
        val type = visit(ast.type).type
        require(type is Type.Reference) { error(ast,
            "Unconstructable type.",
            "The type ${type} cannot be constructed (only reference types)."
        ) }
        val arguments = mutableListOf<RhovasIr.Expression>()
        val function = resolveFunction("constructor", ast, type, "", false, ast.arguments.size) {
            ast.arguments[it.first] to visit(ast.arguments[it.first], it.second).also { arguments.add(it) }.type
        }
        RhovasIr.Expression.Invoke.Constructor(type as Type.Reference, function, arguments, function.returns)
    }

    override fun visit(ast: RhovasAst.Expression.Invoke.Function): RhovasIr.Expression.Invoke.Function = analyzeAst(ast) {
        val qualifier = ast.qualifier?.let { visit(it).type }
        val arguments = mutableListOf<RhovasIr.Expression>()
        val function = resolveFunction("function", ast, qualifier, ast.name, true, ast.arguments.size) {
            Pair(ast.arguments[it.first], visit(ast.arguments[it.first], it.second).also { arguments.add(it) }.type)
        }
        val bang = ast.name.endsWith('!')
        val returns = computeBangReturn(function, bang) { error(ast,
            "Invalid bang attribute.",
            "A bang attribute requires the function ${ast.name} to return type Result, but received ${function.returns}."
        ) }
        RhovasIr.Expression.Invoke.Function(qualifier, function, bang, arguments, returns)
    }

    override fun visit(ast: RhovasAst.Expression.Invoke.Method): RhovasIr.Expression.Invoke.Method = analyzeAst(ast) {
        val receiver = visit(ast.receiver)
        val receiverType = computeCoalesceReceiver(receiver, ast.coalesce)
        val (method, arguments) = resolveMethod(ast, ast.receiver, receiverType, ast.name, true, ast.arguments)
        val bang = ast.name.endsWith('!')
        val returns = computeBangReturn(method.function, bang) { error(ast,
            "Invalid bang attribute.",
            "A bang attribute requires the method ${ast.name} to return type Result, but received ${method.returns}."
        ) }
        val type = computeCoalesceCascadeReturn(returns, receiver.type, ast.coalesce, ast.cascade)
        RhovasIr.Expression.Invoke.Method(receiver, method, bang, ast.coalesce, ast.cascade, arguments, type)
    }

    override fun visit(ast: RhovasAst.Expression.Invoke.Pipeline): RhovasIr.Expression.Invoke.Pipeline = analyzeAst(ast) {
        val receiver = visit(ast.receiver)
        val receiverType = computeCoalesceReceiver(receiver, ast.coalesce)
        val qualifier = ast.qualifier?.let { visit(it).type }
        val arguments = mutableListOf<RhovasIr.Expression>()
        val function = resolveFunction("function", ast, qualifier, ast.name, true, ast.arguments.size + 1) {
            when (it.first) {
                0 -> Pair(ast.receiver, receiverType)
                else -> Pair(ast.arguments[it.first - 1], visit(ast.arguments[it.first - 1], it.second).also { arguments.add(it) }.type)
            }
        }
        val bang = ast.name.endsWith('!')
        val returns = computeBangReturn(function, bang) { error(ast,
            "Invalid bang attribute.",
            "A bang attribute requires the function ${ast.name} to return type Result, but received ${function.returns}."
        ) }
        val type = computeCoalesceCascadeReturn(returns, receiver.type, ast.coalesce, ast.cascade)
        RhovasIr.Expression.Invoke.Pipeline(receiver, qualifier, function, bang, ast.coalesce, ast.cascade, arguments, type)
    }

    private fun computeCoalesceReceiver(receiver: RhovasIr.Expression, coalesce: Boolean): Type {
        return when {
            coalesce -> receiver.type.generic("T", Type.RESULT.GENERIC) ?: throw error(
                "Invalid coalesce.",
                "Coalescing requires the receiver to be type Result, but received ${receiver.type}.",
                receiver.context.firstOrNull(),
            )
            else -> receiver.type
        }
    }

    private fun computeBangReturn(function: Function, bang: Boolean, error: () -> AnalyzeException): Type {
        return when {
            bang && function.throws.isEmpty() -> function.returns.generic("T", Type.RESULT.GENERIC) ?: throw error()
            !bang && function.throws.isNotEmpty() -> Type.RESULT[function.returns, function.throws.singleOrNull() ?: Type.EXCEPTION]
            else -> function.returns
        }
    }

    private fun computeCoalesceCascadeReturn(returns: Type, receiver: Type, coalesce: Boolean, cascade: Boolean): Type {
        return when {
            cascade -> receiver
            coalesce -> when {
                returns.isSubtypeOf(Type.RESULT.DYNAMIC) -> returns
                receiver.isSubtypeOf(Type.NULLABLE.DYNAMIC) -> Type.NULLABLE[returns]
                else -> Type.RESULT[returns, receiver.generic("E", Type.RESULT.GENERIC)!!]
            }
            else -> returns
        }
    }

    private fun resolveFunction(
        term: String,
        ast: RhovasAst,
        qualifier: Type?,
        name: String,
        bang: Boolean,
        arity: Int,
        generator: (Pair<Int, Type>) -> Pair<RhovasAst.Expression, Type>,
    ): Function {
        val descriptor = listOfNotNull(qualifier ?: "", name.takeIf { it.isNotEmpty() }).joinToString(".") + "/" + (if (term == "method") arity - 1 else arity)
        val candidates = (qualifier?.functions?.get(name, arity) ?: context.scope.functions[name, arity])
            .ifEmpty {
                if (bang) {
                    val variant = if (name.endsWith("!")) name.removeSuffix("!") else "${name}!"
                    qualifier?.functions?.get(variant, arity) ?: context.scope.functions[variant, arity]
                } else listOf()
            }
            .map { Pair(it, mutableMapOf<String, Type>()) }
            .ifEmpty { throw error(ast,
                "Undefined ${term}.",
                "The ${term} ${descriptor} is not defined.",
            ) }
        val filtered = candidates.toMutableList()
        val arguments = mutableListOf<Type>()
        for (i in 0 until arity) {
            val inference = filtered.map { it.first.parameters[i].type.bind(it.second) }.reduceOrNull { acc, type -> acc.unify(type) } ?: Type.ANY
            val (ast, type) = generator(Pair(i, inference)).also { arguments.add(it.second) }
            filtered.retainAll { type.isSubtypeOf(it.first.parameters[i].type, it.second) }
            require(filtered.isNotEmpty()) { when (candidates.size) {
                1 -> error(ast,
                    "Invalid argument.",
                    "The ${term} ${descriptor} requires argument ${i} to be type ${candidates[0].first.parameters[i].type.bind(candidates[0].second)} but received ${arguments[i]}.",
                )
                else -> error(ast,
                    "Unresolved ${term}.",
                    "The ${term} ${descriptor} could not be resolved to one of the available overloads below with arguments (${arguments.joinToString()}):\n${candidates.map { c -> "\n - ${c.first.name}(${c.first.parameters.joinToString { "${it.type}" }})" }}",
                )
            } }
        }
        val function = (qualifier?.functions?.get(filtered.first().first.name, arguments) ?: context.scope.functions[filtered.first().first.name, arguments])!!
        val exceptions = function.throws + listOfNotNull(function.returns.generic("E", Type.RESULT.GENERIC)?.takeIf { name.endsWith('!') })
        exceptions.filter { (it as? Type.Reference)?.component?.name != "Dynamic" }.forEach { exception ->
            require(context.exceptions.any { exception.isSubtypeOf(it) }) { error(ast,
                "Uncaught exception.",
                "An exception is thrown of type ${exception}, but this exception is never caught or declared.",
            ) }
        }
        return function
    }

    private fun resolveMethod(
        ast: RhovasAst,
        receiverAst: RhovasAst.Expression,
        receiverType: Type,
        name: String,
        bang: Boolean,
        argumentAsts: List<RhovasAst.Expression>
    ): Pair<Method, List<RhovasIr.Expression>> {
        val arguments = mutableListOf<RhovasIr.Expression>()
        val function = resolveFunction("method", ast, receiverType, name, bang, argumentAsts.size + 1) {
            when (it.first) {
                0 -> Pair(receiverAst, receiverType)
                else -> Pair(argumentAsts[it.first - 1], visit(argumentAsts[it.first - 1], it.second).also { arguments.add(it) }.type)
            }
        }
        val method = receiverType.methods[function.name, function.parameters.drop(1).map { it.type }]!!
        return Pair(method, arguments)
    }

    override fun visit(ast: RhovasAst.Expression.Invoke.Macro): RhovasIr.Expression = analyzeAst(ast) {
        //Hardcoded #typeof macro for manual debugging
        if (ast.name == "typeof" && ast.arguments.size == 1) {
            val expression = visit(ast.arguments.single())
            return@analyzeAst RhovasIr.Expression.Literal.Type(expression.type, Type.TYPE[expression.type])
        }
        require(ast.dsl != null) { error(ast,
            "Unsupported Macro",
            "Macros without DSLs are not currently supported.",
        ) }
        require(ast.arguments.isEmpty()) { error(ast,
            "Invalid DSL arguments.",
            "DSLs with arguments are not currently supported.",
        ) }
        val function = context.scope.functions[ast.name, listOf(Type.LIST[Type.STRING], Type.LIST.DYNAMIC)] ?: throw error(ast,
            "Undefined DSL transformer.",
            "The DSL ${ast.name} requires a transformer function ${ast.name}(List<String>, List<Dynamic>).",
        )
        val literals = RhovasIr.Expression.Literal.List(
            ast.dsl!!.literals.map { RhovasIr.Expression.Literal.String(listOf(it), listOf(), Type.STRING) },
            Type.LIST[Type.STRING],
        )
        val arguments = RhovasIr.Expression.Literal.List(
            ast.dsl.arguments.map { visit(it as RhovasAst.Expression) },
            Type.LIST.DYNAMIC,
        )
        RhovasIr.Expression.Invoke.Function(null, function, ast.name.endsWith('!'), listOf(literals, arguments), function.returns)
    }

    override fun visit(ast: RhovasAst.Expression.Lambda): RhovasIr.Expression.Lambda = analyzeAst(ast) {
        //TODO(#2): Forward thrown exceptions from context into declaration
        val (inferenceParameters, inferenceReturns, inferenceThrows) = if (context.inference != Type.DYNAMIC && context.inference.isSubtypeOf(Type.LAMBDA.GENERIC)) {
            val parameters = (context.inference.generic("T", Type.LAMBDA.GENERIC)?.generic("T", Type.TUPLE.GENERIC) as? Type.Tuple)?.let {
                Type.Tuple(it.elements.map { it.copy(type = when (it.type) {
                    is Type.Generic -> it.type.bound
                    is Type.Variant -> it.type.upper ?: Type.ANY
                    else -> it.type
                }) })
            }
            val returns = context.inference.generic("R", Type.LAMBDA.GENERIC)
            val throws = context.inference.generic("E", Type.LAMBDA.GENERIC)
            Triple(parameters, returns, throws)
        } else Triple(null, null, null)
        val parameters = ast.parameters.withIndex().map {
            val type = it.value.second?.let { visit(it).type }
                ?: inferenceParameters?.elements?.getOrNull(it.index)?.type
                ?: Type.DYNAMIC
            Variable.Declaration(it.value.first, type)
        }
        val returns = inferenceReturns?.bind(mapOf("R" to Type.DYNAMIC)) ?: Type.DYNAMIC
        val throws = listOfNotNull(inferenceThrows?.bind(mapOf("E" to Type.EXCEPTION)))
        val function = Function.Declaration("lambda", Modifiers(), linkedMapOf(), parameters, returns, throws)
        val body = analyze(context.forFunction(function)) {
            if (parameters.isNotEmpty()) {
                parameters.forEach { (context.scope as Scope.Declaration).variables.define(it) }
            } else {
                val type = inferenceParameters?.takeIf { it.elements.size == 1 }?.let { it.elements[0].type }
                    ?: inferenceParameters?.let { Type.TUPLE[it] }
                    ?: Type.DYNAMIC
                (context.scope as Scope.Declaration).variables.define(Variable.Declaration("val", type))
            }
            visit(ast.body).also {
                require(it.type.isSubtypeOf(function.returns) || context.jumps.contains("")) { analyze(it.context) { error(ast,
                    "Invalid return value type.",
                    "The enclosing function ${function.name}/${function.parameters.size} requires the return value to be type ${function.returns}, but received ${it.type}.",
                ) } }
            }
        }
        val type = Type.LAMBDA[parameters.takeIf { it.isNotEmpty() }?.let { Type.TUPLE[Type.Tuple(it.withIndex().map { it.value.copy(name = it.index.toString()) })] } ?: Type.TUPLE.DYNAMIC, returns, Type.DYNAMIC]
        RhovasIr.Expression.Lambda(parameters, body, type)
    }

    private fun visit(ast: RhovasAst.Pattern, type: Type): RhovasIr.Pattern {
        return analyze(context.with(InferenceContext(type))) { super.visit(ast) as RhovasIr.Pattern }
    }

    override fun visit(ast: RhovasAst.Pattern.Variable): RhovasIr.Pattern.Variable = analyzeAst(ast) {
        require(!context.bindings.containsKey(ast.name)) { error(ast,
            "Redefined pattern binding",
            "The identifier ${ast.name} is already bound in this pattern.",
        ) }
        val variable = if (ast.name != "_") Variable.Declaration(ast.name, context.inference) else null
        variable?.let { context.bindings[it.name] = it }
        RhovasIr.Pattern.Variable(variable)
    }

    override fun visit(ast: RhovasAst.Pattern.Value): RhovasIr.Pattern.Value = analyzeAst(ast) {
        val value = visit(ast.value, context.inference)
        require(value.type.isSubtypeOf(context.inference)) { error(ast,
            "Unmatchable pattern type",
            "This pattern is within a context that requires type ${context.inference}, but received ${value.type}.",
        ) }
        RhovasIr.Pattern.Value(value)
    }

    override fun visit(ast: RhovasAst.Pattern.Predicate): RhovasIr.Pattern.Predicate = analyzeAst(ast) {
        val pattern = visit(ast.pattern, context.inference)
        val predicate = analyze {
            (context.scope as Scope.Declaration).variables.define(Variable.Declaration("val", context.inference))
            pattern.bindings.forEach { (context.scope as Scope.Declaration).variables.define(it.value) }
            visit(ast.predicate, Type.BOOLEAN)
        }
        require(predicate.type.isSubtypeOf(Type.BOOLEAN)) { error(ast.predicate,
            "Invalid pattern predicate type.",
            "A predicate pattern requires the predicate to be type Boolean, but received ${predicate.type}.",
        ) }
        RhovasIr.Pattern.Predicate(pattern, predicate)
    }

    override fun visit(ast: RhovasAst.Pattern.OrderedDestructure): RhovasIr.Pattern.OrderedDestructure = analyzeAst(ast) {
        require(context.inference.isSupertypeOf(Type.LIST.DYNAMIC)) { error(ast,
            "Unmatchable pattern type",
            "This pattern is within a context that requires type ${context.inference}, but received List.",
        ) }
        val type = context.inference.generic("T", Type.LIST.GENERIC) ?: Type.DYNAMIC
        var vararg = false
        val patterns = ast.patterns.map { pattern ->
            if (pattern is RhovasAst.Pattern.VarargDestructure) { analyzeAst(pattern) {
                require(!vararg) { error(pattern,
                    "Invalid multiple varargs.",
                    "An ordered destructure requires no more than one vararg pattern.",
                ) }
                vararg = true
                val p = pattern.pattern?.let {
                    visit(it, type)
                }
                val bindings = p?.bindings?.mapValues { it.value.copy(type = Type.LIST[it.value.type]) } ?: mapOf()
                context.bindings.putAll(bindings)
                RhovasIr.Pattern.VarargDestructure(p, pattern.operator, bindings)
            } } else {
                visit(pattern, type)
            }
        }
        RhovasIr.Pattern.OrderedDestructure(patterns)
    }

    override fun visit(ast: RhovasAst.Pattern.NamedDestructure): RhovasIr.Pattern.NamedDestructure = analyzeAst(ast) {
        require(context.inference.isSupertypeOf(Type.STRUCT.GENERIC)) { error(ast,
            "Unmatchable pattern type",
            "This pattern is within a context that requires type ${context.inference}, but received Struct.",
        ) }
        val type = (context.inference.generic("T", Type.STRUCT.GENERIC) as? Type.Struct)?.fields
        var vararg = false
        val patterns = ast.patterns.map { (key, pattern) ->
            if (pattern is RhovasAst.Pattern.VarargDestructure) {
                Pair(null, analyzeAst(pattern) {
                    require(!vararg) { error(pattern,
                        "Invalid multiple varargs.",
                        "A named destructure requires no more than one vararg pattern.",
                    ) }
                    vararg = true
                    val matched = ast.patterns.mapNotNull { it.first ?: (it.second as? RhovasAst.Pattern.Variable)?.name }
                    val remaining = type?.filter { !matched.contains(it.key) }
                    val p = pattern.pattern?.let {
                        val type = remaining?.map { it.value.type }?.reduceOrNull { acc, type -> acc.unify(type) } ?: Type.DYNAMIC
                        visit(it, type)
                    }
                    val bindings = p?.bindings?.mapValues { b -> Variable.Declaration(b.key, remaining?.let { Type.STRUCT[it.keys.map { it to b.value.type }, b.value.mutable] } ?: Type.STRUCT.GENERIC, b.value.mutable) } ?: mapOf()
                    context.bindings.putAll(bindings)
                    RhovasIr.Pattern.VarargDestructure(p, pattern.operator, bindings)
                })
            } else {
                val key = key
                    ?: (pattern as? RhovasAst.Pattern.Variable)?.name
                    ?: throw error(pattern,
                        "Missing pattern key",
                        "This pattern requires a key to be used within a named destructure.",
                    )
                val pattern = visit(pattern, type?.get(key)?.type ?: Type.DYNAMIC)
                context.bindings[key] = Variable.Declaration(key, type?.get(key)?.type ?: Type.DYNAMIC)
                Pair(key, pattern)
            }
        }
        RhovasIr.Pattern.NamedDestructure(patterns)
    }

    override fun visit(ast: RhovasAst.Pattern.TypedDestructure): RhovasIr.Pattern.TypedDestructure = analyzeAst(ast) {
        val type = visit(ast.type).type
        require(context.inference.isSupertypeOf(type)) { error(ast,
            "Unmatchable pattern type",
            "This pattern is within a context that requires type ${context.inference}, but received ${type}.",
        ) }
        val pattern = ast.pattern?.let {
            visit(it, type)
        }
        RhovasIr.Pattern.TypedDestructure(type, pattern)
    }

    override fun visit(ast: RhovasAst.Pattern.VarargDestructure): RhovasIr.Pattern.VarargDestructure {
        throw AssertionError()
    }

    fun visit(ast: RhovasAst.Type): RhovasIr.Type {
        return super.visit(ast) as RhovasIr.Type
    }

    override fun visit(ast: RhovasAst.Type.Reference): RhovasIr.Type = analyzeAst(ast) {
        var type = context.scope.types[ast.path.first()] ?: throw error(ast,
            "Undefined type.",
            "The type ${ast.path.first()} is not defined in the current scope.",
        )
        ast.path.drop(1).forEach {
            val component = (type as? Type.Reference)?.component ?: throw error(ast,
                "Invalid type qualifier.",
                "Qualified types require a reference type, but received a base type of Type.${type::class.simpleName} (${type}).",
            )
            type = component.scope.types[it] ?: throw error(ast,
                "Undefined type.",
                "The type ${it} is not defined in ${type}.",
            )
        }
        if (ast.generics != null) {
            val component = (type as? Type.Reference)?.component ?: throw error(ast,
                "Invalid generic type.",
                "Generic types require a reference type, but received a base type of Type.${type::class.simpleName} (${type}).",
            )
            require(ast.generics.size == component.generics.size) { error(ast,
                "Invalid generic parameters.",
                "The type ${type} requires ${component.generics.size} generic parameters, but received ${ast.generics.size}.",
            ) }
            val generics = component.generics.values.withIndex().associate {
                val generic = visit(ast.generics[it.index]).type
                require(generic.isSubtypeOf(it.value.bound)) { error(ast.generics[it.index],
                    "Invalid generic parameter.",
                    "The type ${type} requires generic parameter ${it.index} to be type ${it.value.bound}, but received ${generic}.",
                ) }
                it.value.name to generic
            }
            type = Type.Reference(component, generics)
        }
        if (ast.nullable) {
            type = Type.NULLABLE[type]
        }
        RhovasIr.Type(type)
    }

    override fun visit(ast: RhovasAst.Type.Tuple): RhovasIr.Type = analyzeAst(ast) {
        val elements = ast.elements.map { visit(it).type }
        RhovasIr.Type(Type.TUPLE[elements].generics["T"]!!)
    }

    override fun visit(ast: RhovasAst.Type.Struct): RhovasIr = analyzeAst(ast) {
        val fields = mutableMapOf<String, Type>()
        ast.fields.forEach {
            require(fields[it.first] == null) { error(ast,
                "Redefined struct field.",
                "The property ${it.first} has already been defined for this object.",
            ) }
            fields[it.first] = visit(it.second).type
        }
        RhovasIr.Type(Type.STRUCT[fields.map { it.key to it.value }].generics["T"]!!)
    }

    override fun visit(ast: RhovasAst.Type.Variant): RhovasIr = analyzeAst(ast) {
        val lower = ast.lower?.let { visit(it).type }
        val upper = ast.upper?.let { visit(it).type }
        RhovasIr.Type(Type.Variant(lower, upper))
    }

    internal fun <T> analyze(context: List<Input.Range>, analyzer: () -> T): T {
        context.firstOrNull()?.let { this.context.inputs.addLast(it) }
        return analyzer().also { context.firstOrNull()?.let { this.context.inputs.removeLast() } }
    }

    private fun <T : RhovasIr> analyzeAst(ast: RhovasAst, analyzer: () -> T): T {
        return analyze(ast.context, analyzer).also { it.context = ast.context }
    }

}
