package dev.rhovas.interpreter.analyzer.rhovas

import com.ionspin.kotlin.bignum.decimal.BigDecimal
import com.ionspin.kotlin.bignum.integer.BigInteger
import dev.rhovas.interpreter.analyzer.AnalyzeException
import dev.rhovas.interpreter.analyzer.Analyzer
import dev.rhovas.interpreter.environment.*
import dev.rhovas.interpreter.environment.Function
import dev.rhovas.interpreter.library.Library
import dev.rhovas.interpreter.parser.Input
import dev.rhovas.interpreter.parser.dsl.DslAst
import dev.rhovas.interpreter.parser.rhovas.RhovasAst

class RhovasAnalyzer(scope: Scope<out Variable, out Function>) :
    Analyzer(Context(listOf(
        InputContext(ArrayDeque()),
        ScopeContext(scope),
        InferenceContext(null),
        InitializationContext(mutableMapOf()),
        FunctionContext(null),
        LabelContext(mutableSetOf()),
        JumpContext(mutableSetOf()),
        ExceptionContext(mutableSetOf(Type.EXCEPTION)),
    ).associateBy { it::class.simpleName!! })),
    RhovasIr.DefinitionPhase.Visitor<RhovasIr> {

    private val declare = DeclarePhase()
    private val define = DefinePhase()

    private val Context.scope get() = this[ScopeContext::class]
    private val Context.inference get() = this[InferenceContext::class]
    private val Context.initialization get() = this[InitializationContext::class]
    private val Context.function get() = this[FunctionContext::class]
    private val Context.labels get() = this[LabelContext::class]
    private val Context.jumps get() = this[JumpContext::class]
    private val Context.exceptions get() = this[ExceptionContext::class]
    private val Context.pattern get() = this[PatternContext::class]

    /**
     * Context for variable/function/type scope.
     */
    data class ScopeContext(
        val scope: Scope<out Variable, out Function>,
    ) : Context.Item<Scope<out Variable, out Function>>(scope) {

        override fun child(): ScopeContext {
            return ScopeContext(Scope.Declaration(scope))
        }

        override fun merge(children: List<Scope<out Variable, out Function>>) {}

    }

    /**
     * Context for storing the expected type for type inference.
     */
    data class InferenceContext(
        val type: Type?,
    ) : Context.Item<Type?>(type) {

        override fun child(): InferenceContext {
            return this
        }

        override fun merge(children: List<Type?>) {}

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
     * Context for storing the inferred type and variable bindings for patterns.
     */
    data class PatternContext(
        val type: Type,
        val bindings: MutableMap<String, Type>,
    ) : Context.Item<PatternContext.Data>(Data(type, bindings)) {

        data class Data(
            val type: Type,
            val bindings: MutableMap<String, Type>,
        )

        override fun child(): Context.Item<Data> {
            return this
        }

        override fun merge(children: List<Data>) {}

    }

    override fun visit(ast: RhovasAst.Source): RhovasIr.Source {
        val imports = ast.imports.map { visit(it) }
        val statements = ast.statements.map { visit(it) }
        return RhovasIr.Source(imports, statements).also {
            it.context = ast.context
        }
    }

    override fun visit(ast: RhovasAst.Import): RhovasIr.Import {
        val type = Library.TYPES[ast.path.joinToString(".")] ?: throw error(
            ast,
            "Undefined type.",
            "The type ${ast.path.joinToString(".")} is not defined."
        )
        val alias = ast.alias ?: ast.path.last()
        require(!context.scope.types.isDefined(alias, true)) { error(
            ast,
            "Redefined type.",
            "The type ${ast.path.last()} is already defined in the current scope.",
        ) }
        context.scope.types.define(type, alias)
        return RhovasIr.Import(type).also {
            it.context = ast.context
        }
    }

    private fun visit(ast: RhovasAst.Component): RhovasIr.Component {
        return super.visit(ast) as RhovasIr.Component
    }

    override fun visit(ast: RhovasAst.Component.Struct): RhovasIr.Component.Struct {
        return ast.also { declare.visit(it) }.let { define.visit(it) }.let { visit(it) }
    }

    override fun visit(ir: RhovasIr.DefinitionPhase.Component.Struct): RhovasIr.Component.Struct {
        ir.ast.context.firstOrNull()?.let { context.inputs.addLast(it) }
        val type = context.scope.types[ir.ast.name]!!
        val members = ir.members.map { visit(it) }
        return RhovasIr.Component.Struct(type, members).also {
            it.context = ir.ast.context
            it.context.firstOrNull()?.let { context.inputs.removeLast() }
        }
    }

    override fun visit(ast: RhovasAst.Component.Class): RhovasIr.Component.Class {
        return ast.also { declare.visit(it) }.let { define.visit(it) }.let { visit(it) }
    }

    override fun visit(ir: RhovasIr.DefinitionPhase.Component.Class): RhovasIr.Component.Class {
        ir.ast.context.firstOrNull()?.let { context.inputs.addLast(it) }
        val type = context.scope.types[ir.ast.name]!!
        val members = ir.members.map { visit(it) }
        return RhovasIr.Component.Class(type, members).also {
            it.context = ir.ast.context
            it.context.firstOrNull()?.let { context.inputs.removeLast() }
        }
    }

    private fun visit(ir: RhovasIr.DefinitionPhase.Member): RhovasIr.Member {
        return super.visit(ir) as RhovasIr.Member
    }

    override fun visit(ir: RhovasIr.DefinitionPhase.Member.Property): RhovasIr.Member.Property {
        ir.ast.context.firstOrNull()?.let { context.inputs.addLast(it) }
        val value = ir.ast.value?.let { visit(it, ir.getter.returns) }
        require(value == null || value.type.isSubtypeOf(ir.getter.returns)) { error(
            ir.ast,
            "Invalid value type.",
            "The property ${ir.ast.name} requires a value of type ${ir.getter.returns}, but received ${value!!.type}."
        ) }
        return RhovasIr.Member.Property(ir.getter, ir.setter, value).also {
            it.context = ir.ast.context
            it.context.firstOrNull()?.let { context.inputs.removeLast() }
        }
    }

    override fun visit(ir: RhovasIr.DefinitionPhase.Member.Initializer): RhovasIr.Member.Initializer {
        ir.ast.context.firstOrNull()?.let { context.inputs.addLast(it) }
        ir.ast.context.firstOrNull()?.let { context.inputs.addLast(it) }
        return analyze(context.with(
            ScopeContext(Scope.Declaration(context.scope)),
            FunctionContext(ir.function.declaration),
            ExceptionContext(ir.function.throws.toMutableSet()),
        )) {
            (context.scope as Scope.Declaration).variables.define(Variable.Declaration("this", ir.function.returns, false))
            context.initialization["this"] = InitializationContext.Data(false, RhovasAst.Statement.Declaration.Variable(false, "this", null, null).also { it.context = ir.ast.context }, mutableListOf())
            ir.function.parameters.forEach { (context.scope as Scope.Declaration).variables.define(it) }
            val block = visit(ir.ast.block)
            RhovasIr.Member.Initializer(ir.function, block).also {
                it.context = ir.ast.context
                it.context.firstOrNull()?.let { context.inputs.removeLast() }
            }
        }
    }

    override fun visit(ir: RhovasIr.DefinitionPhase.Member.Method): RhovasIr {
        val function = visit(ir.function)
        return RhovasIr.Member.Method(function).also {
            it.context = ir.ast.context
        }
    }

    private fun visit(ast: RhovasAst.Statement): RhovasIr.Statement {
        return super.visit(ast) as RhovasIr.Statement
    }

    override fun visit(ast: RhovasAst.Statement.Component): RhovasIr {
        return RhovasIr.Statement.Component(visit(ast.component)).also {
            it.context = ast.context
        }
    }

    override fun visit(ast: RhovasAst.Statement.Initializer): RhovasIr {
        ast.context.firstOrNull()?.let { context.inputs.addLast(it) }
        require(context.function?.name == "") { error(ast,
            "Invalid initializer.",
            "An instance initializer can only be called within an initializer function.",
        ) }
        val initialization = context.initialization["this"]!!
        require(!initialization.initialized) {
            initialization.declaration.context.firstOrNull()?.let { context.inputs.add(it) }
            error(ast,
                "Reinitialized instance.",
                "An instance initializer can only be called once.",
            )
        }
        val initializer = visit(ast.initializer, Type.STRUCT.ANY) as RhovasIr.Expression.Literal.Object
        //TODO(#14): Validate available fields
        return RhovasIr.Statement.Initializer(initializer).also {
            it.context = ast.context
            it.context.firstOrNull()?.let { context.inputs.removeLast() }
        }
    }

    override fun visit(ast: RhovasAst.Statement.Expression): RhovasIr {
        ast.context.firstOrNull()?.let { context.inputs.addLast(it) }
        require(ast.expression is RhovasAst.Expression.Block || ast.expression is RhovasAst.Expression.Invoke) { error(
            ast.expression,
            "Invalid expression statement.",
            "An expression statement requires an invoke expression in order to perform a useful side-effect, but received ${ast.expression::class.simpleName}.",
        ) }
        val expression = visit(ast.expression)
        return RhovasIr.Statement.Expression(expression).also {
            it.context = ast.context
            it.context.firstOrNull()?.let { context.inputs.removeLast() }
        }
    }

    override fun visit(ast: RhovasAst.Statement.Declaration.Variable): RhovasIr.Statement.Declaration.Variable {
        ast.context.firstOrNull()?.let { context.inputs.addLast(it) }
        require(context.scope.variables[ast.name, true] == null) { error(
            ast,
            "Redefined variable.",
            "The variable ${ast.name} is already defined in this scope.",
        ) }
        require(ast.type != null || ast.value != null) { error(
            ast,
            "Undefined variable type.",
            "A variable declaration requires either a type or an initial value.",
        ) }
        val type = ast.type?.let { visit(it).type }
        val value = ast.value?.let { visit(it, type) }
        require(type == null || value == null || value.type.isSubtypeOf(type)) { error(
            ast,
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
        return RhovasIr.Statement.Declaration.Variable(variable, value).also {
            it.context = ast.context
            it.context.firstOrNull()?.let { context.inputs.removeLast() }
        }
    }

    override fun visit(ast: RhovasAst.Statement.Declaration.Function): RhovasIr.Statement.Declaration.Function {
        return define.visit(ast).let { visit(it) }
    }

    override fun visit(ir: RhovasIr.DefinitionPhase.Function): RhovasIr.Statement.Declaration.Function {
        ir.ast.context.firstOrNull()?.let { context.inputs.addLast(it) }
        return analyze(context.with(
            ScopeContext(Scope.Declaration(context.scope)),
            FunctionContext(ir.function),
            ExceptionContext(ir.function.throws.toMutableSet()),
        )) {
            ir.function.generics.forEach { context.scope.types.define(it) }
            ir.function.parameters.forEach { (context.scope as Scope.Declaration).variables.define(it) }
            val block = visit(ir.ast.block)
            require(ir.function.returns.isSubtypeOf(Type.VOID) || context.jumps.contains("")) { error(
                ir.ast,
                "Missing return value.",
                "The function ${ir.ast.name}/${ir.ast.parameters.size} requires a return value.",
            ) }
            context.jumps.clear()
            RhovasIr.Statement.Declaration.Function(ir.function, block).also {
                it.context = ir.ast.context
                it.context.firstOrNull()?.let { context.inputs.removeLast() }
            }
        }
    }

    override fun visit(ast: RhovasAst.Statement.Assignment): RhovasIr.Statement.Assignment {
        ast.context.firstOrNull()?.let { context.inputs.addLast(it) }
        require(ast.receiver is RhovasAst.Expression.Access) { error(
            ast.receiver,
            "Invalid assignment receiver.",
            "An assignment statement requires the receiver to be an access expression, but received ${ast.receiver::class.simpleName}.",
        ) }
        return when (ast.receiver) {
            is RhovasAst.Expression.Access.Variable -> {
                val initialization = context.initialization[ast.receiver.name]
                val receiver = when {
                    initialization == null -> visit(ast.receiver).also {
                        require(it.variable.mutable) { error(
                            ast.receiver,
                            "Unassignable variable.",
                            "The variable ${it.variable.name} is not assignable.",
                        ) }
                    }
                    initialization.initialized -> visit(ast.receiver).also {
                        require(it.variable.mutable) { error(
                            ast.receiver,
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
                require(value.type.isSubtypeOf(receiver.variable.type)) { error(
                    ast.value,
                    "Invalid assignment value type.",
                    "The variable ${receiver.variable.name} requires the value to be type ${receiver.variable.type}, but received ${value.type}.",
                ) }
                RhovasIr.Statement.Assignment.Variable(receiver.variable, value).also {
                    it.context = ast.context
                    it.context.firstOrNull()?.let { context.inputs.removeLast() }
                }
            }
            is RhovasAst.Expression.Access.Property -> {
                require(!ast.receiver.coalesce) { error(
                    ast.receiver,
                    "Invalid assignment receiver.",
                    "An assignment statement requires the receiver property access to be non-coalescing.",
                ) }
                val receiver = visit(ast.receiver)
                require(receiver.property.mutable) { error(
                    ast.receiver,
                    "Unassignable property.",
                    "The property ${receiver.property.type.base.name}.${receiver.property.name} is not assignable.",
                ) }
                val value = visit(ast.value, receiver.property.type)
                require(value.type.isSubtypeOf(receiver.property.type)) { error(
                    ast.value,
                    "Invalid assignment value type.",
                    "The property ${receiver.property.type.base.name}.${receiver.property.name} requires the value to be type ${receiver.property.type}, but received ${value.type}.",
                ) }
                RhovasIr.Statement.Assignment.Property(receiver.receiver, receiver.property, value).also {
                    it.context = ast.context
                    it.context.firstOrNull()?.let { context.inputs.removeLast() }
                }
            }
            is RhovasAst.Expression.Access.Index -> {
                val receiver = visit(ast.receiver.receiver)
                val (method, arguments) = resolveMethod(ast.receiver, ast.receiver.receiver, receiver.type, "[]=", false, ast.receiver.arguments + listOf(ast.value))
                RhovasIr.Statement.Assignment.Index(receiver, method, arguments.dropLast(1), arguments.last()).also {
                    it.context = ast.context
                    it.context.firstOrNull()?.let { context.inputs.removeLast() }
                }
            }
            else -> throw AssertionError()
        }
    }

    override fun visit(ast: RhovasAst.Statement.If): RhovasIr.Statement.If {
        ast.context.firstOrNull()?.let { context.inputs.addLast(it) }
        val condition = visit(ast.condition, Type.BOOLEAN)
        require(condition.type.isSubtypeOf(Type.BOOLEAN)) { error(
            ast.condition,
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
        return RhovasIr.Statement.If(condition, thenBlock, elseBlock).also {
            it.context = ast.context
            it.context.firstOrNull()?.let { context.inputs.removeLast() }
        }
    }

    override fun visit(ast: RhovasAst.Statement.Match.Conditional): RhovasIr {
        ast.context.firstOrNull()?.let { context.inputs.addLast(it) }
        fun visitCondition(ast: RhovasAst.Expression): RhovasIr.Expression {
            val condition = visit(ast, Type.BOOLEAN)
            require(condition.type.isSubtypeOf(Type.BOOLEAN)) { error(
                ast,
                "Invalid match condition type.",
                "A conditional match statement requires the condition to be type Boolean, but received ${condition.type}.",
            ) }
            return condition
        }
        val cases = ast.cases.map {
            analyze(context.child()) {
                it.first.context.firstOrNull()?.let { context.inputs.addLast(it) }
                val condition = visitCondition(it.first)
                val statement = visit(it.second)
                it.first.context.firstOrNull()?.let { context.inputs.removeLast() }
                Pair(condition, statement)
            }
        }
        val elseCase = analyze(context.child()) {
            ast.elseCase?.let {
                (it.first ?: it.second).context.firstOrNull()?.let { context.inputs.addLast(it) }
                val condition = it.first?.let { visitCondition(it) }
                val statement = visit(it.second)
                (it.first ?: it.second).context.firstOrNull()?.let { context.inputs.removeLast() }
                Pair(condition, statement)
            }
        }
        context.merge()
        return RhovasIr.Statement.Match.Conditional(cases, elseCase).also {
            it.context = ast.context
            it.context.firstOrNull()?.let { context.inputs.removeLast() }
        }
    }

    override fun visit(ast: RhovasAst.Statement.Match.Structural): RhovasIr {
        ast.context.firstOrNull()?.let { context.inputs.add(it) }
        val argument = visit(ast.argument)
        //TODO(#10): Pattern coverage/exhaustiveness
        val cases = ast.cases.map {
            analyze(context.child().with(PatternContext(argument.type, mutableMapOf()))) {
                it.first.context.firstOrNull()?.let { context.inputs.addLast(it) }
                val pattern = visit(it.first)
                context.pattern.bindings.forEach {
                    (context.scope as Scope.Declaration).variables.define(Variable.Declaration(it.key, it.value, false))
                }
                val statement = visit(it.second)
                it.first.context.firstOrNull()?.let { context.inputs.removeLast() }
                Pair(pattern, statement)
            }
        }
        val elseCase = ast.elseCase?.let {
            analyze(context.child().with(PatternContext(argument.type, mutableMapOf()))) {
                (it.first ?: it.second).context.firstOrNull()?.let { context.inputs.addLast(it) }
                val pattern = it.first?.let { visit(it) }
                context.pattern.bindings.forEach {
                    (context.scope as Scope.Declaration).variables.define(Variable.Declaration(it.key, it.value, false))
                }
                val statement = visit(it.second)
                (it.first ?: it.second).context.firstOrNull()?.let { context.inputs.removeLast() }
                Pair(pattern, statement)
            }
        }
        context.merge()
        return RhovasIr.Statement.Match.Structural(argument, cases, elseCase).also {
            it.context = ast.context
            it.context.firstOrNull()?.let { context.inputs.removeLast() }
        }
    }

    override fun visit(ast: RhovasAst.Statement.For): RhovasIr.Statement.For {
        ast.context.firstOrNull()?.let { context.inputs.addLast(it) }
        val argument = visit(ast.argument, Type.LIST.ANY)
        require(argument.type.isSubtypeOf(Type.LIST.ANY)) { error(
            ast.argument,
            "Invalid for loop argument type.",
            "A for loop requires the argument to be type List, but received ${argument.type}.",
        ) }
        val type = argument.type.methods["get", listOf(Type.INTEGER)]!!.returns
        val variable = Variable.Declaration(ast.name, type, false)
        return analyze {
            (context.scope as Scope.Declaration).variables.define(variable)
            context.labels.add(null)
            val block = visit(ast.block)
            context.jumps.clear()
            RhovasIr.Statement.For(variable, argument, block).also {
                it.context = ast.context
                it.context.firstOrNull()?.let { context.inputs.removeLast() }
            }
        }
    }

    override fun visit(ast: RhovasAst.Statement.While): RhovasIr.Statement.While {
        ast.context.firstOrNull()?.let { context.inputs.addLast(it) }
        val condition = visit(ast.condition, Type.BOOLEAN)
        require(condition.type.isSubtypeOf(Type.BOOLEAN)) { error(
            ast.condition,
            "Invalid while condition type.",
            "An while statement requires the condition to be type Boolean, but received ${condition.type}.",
        ) }
        return analyze {
            context.labels.add(null)
            val block = visit(ast.block)
            context.jumps.clear()
            RhovasIr.Statement.While(condition, block).also {
                it.context = ast.context
                it.context.firstOrNull()?.let { context.inputs.removeLast() }
            }
        }
    }

    override fun visit(ast: RhovasAst.Statement.Try): RhovasIr.Statement.Try {
        ast.context.firstOrNull()?.let { context.inputs.addLast(it) }
        val child = context.child()
        ast.catchBlocks.forEach {
            it.context.firstOrNull()?.let { context.inputs.addLast(it) }
            val type = visit(it.type).type
            require(type.isSubtypeOf(Type.EXCEPTION)) { error(
                it.type,
                "Invalid catch type",
                "An catch block requires the type to be a subtype of Exception, but received ${type}."
            ) }
            child.exceptions.add(type)
            it.context.firstOrNull()?.let { context.inputs.removeLast() }
        }
        val tryBlock = analyze(child) { visit(ast.tryBlock) }
        val catchBlocks = ast.catchBlocks.map {
            it.context.firstOrNull()?.let { context.inputs.addLast(it) }
            val type = visit(it.type).type //validated as subtype of Exception above
            val variable = Variable.Declaration(it.name, type, false)
            analyze(context.child()) {
                (context.scope as Scope.Declaration).variables.define(variable)
                val block = visit(it.block)
                RhovasIr.Statement.Try.Catch(variable, block).also {
                    it.context = it.context
                    it.context.firstOrNull()?.let { context.inputs.removeLast() }
                }
            }
        }
        context.merge()
        val finallyBlock = ast.finallyBlock?.let {
            analyze(context.with(ExceptionContext(mutableSetOf()))) {
                visit(it)
            }
        }
        return RhovasIr.Statement.Try(tryBlock, catchBlocks, finallyBlock).also {
            it.context = ast.context
            it.context.firstOrNull()?.let { context.inputs.removeLast() }
        }
    }

    override fun visit(ast: RhovasAst.Statement.Try.Catch): RhovasIr.Statement.Try.Catch {
        throw AssertionError()
    }

    override fun visit(ast: RhovasAst.Statement.With): RhovasIr.Statement.With {
        ast.context.firstOrNull()?.let { context.inputs.addLast(it) }
        val argument = visit(ast.argument)
        val variable = ast.name?.let { Variable.Declaration(ast.name, argument.type, false) }
        return analyze {
            variable?.let { (context.scope as Scope.Declaration).variables.define(it) }
            val block = visit(ast.block)
            RhovasIr.Statement.With(variable, argument, block).also {
                it.context = ast.context
                it.context.firstOrNull()?.let { context.inputs.removeLast() }
            }
        }
    }

    override fun visit(ast: RhovasAst.Statement.Label): RhovasIr.Statement.Label {
        ast.context.firstOrNull()?.let { context.inputs.addLast(it) }
        require(ast.statement is RhovasAst.Statement.For || ast.statement is RhovasAst.Statement.While) { error(
            ast.statement,
            "Invalid label statement.",
            "A label statement requires the statement to be a for/while loop.",
        ) }
        require(!context.labels.contains(ast.label)) { error(
            ast,
            "Redefined label.",
            "The label ${ast.label} is already defined in this scope.",
        ) }
        return analyze {
            context.labels.add(ast.label)
            val statement = visit(ast.statement)
            context.jumps.remove(ast.label)
            RhovasIr.Statement.Label(ast.label, statement).also {
                it.context = ast.context
                it.context.firstOrNull()?.let { context.inputs.removeLast() }
            }
        }
    }

    override fun visit(ast: RhovasAst.Statement.Break): RhovasIr.Statement.Break {
        ast.context.firstOrNull()?.let { context.inputs.addLast(it) }
        require(context.labels.contains(null)) { error(
            ast,
            "Invalid continue statement.",
            "A continue statement requires an enclosing for/while loop.",
        ) }
        require(context.labels.contains(ast.label)) { error(
            ast,
            "Undefined label.",
            "The label ${ast.label} is not defined in this scope.",
        ) }
        context.jumps.add(ast.label)
        return RhovasIr.Statement.Break(ast.label).also {
            it.context = ast.context
            it.context.firstOrNull()?.let { context.inputs.removeLast() }
        }
    }

    override fun visit(ast: RhovasAst.Statement.Continue): RhovasIr.Statement.Continue {
        ast.context.firstOrNull()?.let { context.inputs.addLast(it) }
        require(context.labels.contains(null)) { error(
            ast,
            "Invalid continue statement.",
            "A continue statement requires an enclosing for/while loop.",
        ) }
        require(context.labels.contains(ast.label)) { error(
            ast,
            "Undefined label.",
            "The label ${ast.label} is not defined in this scope.",
        ) }
        context.jumps.add(ast.label)
        return RhovasIr.Statement.Continue(ast.label).also {
            it.context = ast.context
            it.context.firstOrNull()?.let { context.inputs.removeLast() }
        }
    }

    override fun visit(ast: RhovasAst.Statement.Return): RhovasIr.Statement.Return {
        ast.context.firstOrNull()?.let { context.inputs.addLast(it) }
        require(context.function != null) { error(
            ast,
            "Invalid return statement.",
            "A return statement requires an enclosing function definition.",
        ) }
        val value = ast.value?.let { visit(it, context.function!!.returns) }
        require((value?.type ?: Type.VOID).isSubtypeOf(context.function!!.returns)) { error(
            ast,
            "Invalid return value type.",
            "The enclosing function ${context.function!!.name}/${context.function!!.parameters.size} requires the return value to be type ${context.function!!.returns}, but received ${value?.type ?: Type.VOID}.",
        ) }
        context.jumps.add("")
        return RhovasIr.Statement.Return(value).also {
            it.context = ast.context
            it.context.firstOrNull()?.let { context.inputs.removeLast() }
        }
    }

    override fun visit(ast: RhovasAst.Statement.Throw): RhovasIr.Statement.Throw {
        ast.context.firstOrNull()?.let { context.inputs.addLast(it) }
        val exception = visit(ast.exception, Type.EXCEPTION)
        require(exception.type.isSubtypeOf(Type.EXCEPTION)) { error(
            ast.exception,
            "Invalid throw expression type.",
            "An throw statement requires the expression to be type Exception, but received ${exception.type}.",
        ) }
        require(context.exceptions.any { exception.type.isSubtypeOf(it) }) { error(
            ast.exception,
            "Uncaught exception.",
            "An exception is thrown of type ${exception.type}, but this exception is never caught or declared.",
        ) }
        context.jumps.add("")
        return RhovasIr.Statement.Throw(exception).also {
            it.context = ast.context
            it.context.firstOrNull()?.let { context.inputs.removeLast() }
        }
    }

    override fun visit(ast: RhovasAst.Statement.Assert): RhovasIr.Statement.Assert {
        ast.context.firstOrNull()?.let { context.inputs.addLast(it) }
        val condition = visit(ast.condition, Type.BOOLEAN)
        require(condition.type.isSubtypeOf(Type.BOOLEAN)) { error(
            ast.condition,
            "Invalid assert condition type.",
            "An assert statement requires the condition to be type Boolean, but received ${condition.type}.",
        ) }
        val message = ast.message?.let { visit(it, Type.STRING) }
        require(message == null || message.type.isSubtypeOf(Type.STRING)) { error(
            ast.message!!,
            "Invalid assert message type.",
            "An assert statement requires the message to be type String, but received ${message!!.type}.",
        ) }
        return RhovasIr.Statement.Assert(condition, message).also {
            it.context = ast.context
            it.context.firstOrNull()?.let { context.inputs.removeLast() }
        }
    }

    override fun visit(ast: RhovasAst.Statement.Require): RhovasIr.Statement.Require {
        ast.context.firstOrNull()?.let { context.inputs.addLast(it) }
        val condition = visit(ast.condition, Type.BOOLEAN)
        require(condition.type.isSubtypeOf(Type.BOOLEAN)) { error(
            ast.condition,
            "Invalid require condition type.",
            "A require statement requires the condition to be type Boolean, but received ${condition.type}.",
        ) }
        val message = ast.message?.let { visit(it, Type.STRING) }
        require(message == null || message.type.isSubtypeOf(Type.STRING)) { error(
            ast.message!!,
            "Invalid require message type.",
            "A require statement requires the message to be type String, but received ${message!!.type}.",
        ) }
        return RhovasIr.Statement.Require(condition, message).also {
            it.context = ast.context
            it.context.firstOrNull()?.let { context.inputs.removeLast() }
        }
    }

    override fun visit(ast: RhovasAst.Statement.Ensure): RhovasIr.Statement.Ensure {
        ast.context.firstOrNull()?.let { context.inputs.addLast(it) }
        val condition = visit(ast.condition, Type.BOOLEAN)
        require(condition.type.isSubtypeOf(Type.BOOLEAN)) { error(
            ast.condition,
            "Invalid ensure condition type.",
            "An ensure statement requires the condition to be type Boolean, but received ${condition.type}.",
        ) }
        val message = ast.message?.let { visit(it, Type.STRING) }
        require(message == null || message.type.isSubtypeOf(Type.STRING)) { error(
            ast.message!!,
            "Invalid ensure message type.",
            "An ensure statement requires the message to be type String, but received ${message!!.type}.",
        ) }
        return RhovasIr.Statement.Ensure(condition, message).also {
            it.context = ast.context
            it.context.firstOrNull()?.let { context.inputs.removeLast() }
        }
    }

    private fun visit(ast: RhovasAst.Expression, type: Type? = null): RhovasIr.Expression {
        return analyze(context.with(InferenceContext(type))) { super.visit(ast) as RhovasIr.Expression }
    }

    override fun visit(ast: RhovasAst.Expression.Block): RhovasIr.Expression.Block {
        ast.context.firstOrNull()?.let { context.inputs.addLast(it) }
        val statements = ast.statements.withIndex().map {
            require(context.jumps.isEmpty()) { error(
                it.value,
                "Unreachable statement.",
                "The previous statement changes control flow to always jump past this statement.",
            ) }
            visit(it.value)
        }
        val expression = ast.expression?.let {
            require(context.jumps.isEmpty()) { error(
                it,
                "Unreachable statement.",
                "The previous statement changes control flow to always jump past this statement.",
            ) }
            visit(it, context.inference)
        }
        val type = expression?.type ?: Type.VOID
        return RhovasIr.Expression.Block(statements, expression, type).also {
            it.context = ast.context
            it.context.firstOrNull()?.let { context.inputs.removeLast() }
        }
    }

    override fun visit(ast: RhovasAst.Expression.Literal.Scalar): RhovasIr {
        val type = when (ast.value) {
            null -> Type.NULLABLE.ANY
            is Boolean -> Type.BOOLEAN
            is BigInteger -> Type.INTEGER
            is BigDecimal -> Type.DECIMAL
            is RhovasAst.Atom -> Type.ATOM
            else -> throw AssertionError()
        }
        return RhovasIr.Expression.Literal.Scalar(ast.value, type).also {
            it.context = ast.context
        }
    }

    override fun visit(ast: RhovasAst.Expression.Literal.String): RhovasIr {
        val arguments = ast.arguments.map { visit(it, Type.STRING) }
        val type = Type.STRING
        return RhovasIr.Expression.Literal.String(ast.literals, arguments, type).also {
            it.context = ast.context
        }
    }

    override fun visit(ast: RhovasAst.Expression.Literal.List): RhovasIr {
        ast.context.firstOrNull()?.let { context.inputs.addLast(it) }
        //check base type to avoid subtype issues with Dynamic
        val (elements, type) = if (context.inference?.base == Type.TUPLE.ANY.base) {
            val generics = ((context.inference as Type.Reference).generics[0] as? Type.Tuple)?.elements
            val elements = ast.elements.withIndex().map { visit(it.value, generics?.getOrNull(it.index)?.type) }
            val type = Type.Tuple(elements.withIndex().map { Variable.Declaration(it.index.toString(), it.value.type, false) })
            Pair(elements, Type.TUPLE[type])
        } else {
            val inference = if (context.inference?.base == Type.LIST.ANY.base) (context.inference as Type.Reference).generics[0] else null
            val elements = ast.elements.map { visit(it, inference) }
            //rough implementation of unification
            val type = elements.fold(inference ?: elements.firstOrNull()?.type ?: Type.DYNAMIC) { acc, expr ->
                when {
                    acc.isSubtypeOf(expr.type) -> expr.type
                    acc.isSupertypeOf(expr.type) -> acc
                    else -> Type.ANY
                }
            }
            Pair(elements, Type.LIST[type])
        }
        return RhovasIr.Expression.Literal.List(elements, type).also {
            it.context = ast.context
            it.context.firstOrNull()?.let { context.inputs.removeLast() }
        }
    }

    override fun visit(ast: RhovasAst.Expression.Literal.Object): RhovasIr {
        ast.context.firstOrNull()?.let { context.inputs.addLast(it) }
        //check base type to avoid subtype issues with Dynamic
        val (properties, type) = if (context.inference?.base == Type.STRUCT.ANY.base) {
            val generics = ((context.inference as Type.Reference).generics[0] as? Type.Struct)?.fields
            val properties = mutableMapOf<String, RhovasIr.Expression>()
            ast.properties.forEach {
                require(properties[it.first] == null) { error(ast,
                    "Redefined object property.",
                    "The property ${it.first} has already been defined for this object.",
                ) }
                properties[it.first] = visit(it.second, generics?.get(it.first)?.type)
            }
            val type = Type.Struct(properties.entries.associate { it.key to Variable.Declaration(it.key, it.value.type, false) })
            Pair(properties, Type.STRUCT[type])
        } else {
            val properties = mutableMapOf<String, RhovasIr.Expression>()
            ast.properties.forEach {
                require(properties[it.first] == null) { error(ast,
                    "Redefined object property.",
                    "The property ${it.first} has already been defined for this object.",
                ) }
                properties[it.first] = visit(it.second)
            }
            Pair(properties, Type.OBJECT)
        }
        return RhovasIr.Expression.Literal.Object(properties, type).also {
            it.context = ast.context
            it.context.firstOrNull()?.let { context.inputs.removeLast() }
        }
    }

    override fun visit(ast: RhovasAst.Expression.Literal.Type): RhovasIr.Expression.Literal.Type {
        val type = visit(ast.type).type
        val expressionType = Type.TYPE[type]
        return RhovasIr.Expression.Literal.Type(type, expressionType).also {
            it.context = ast.context
        }
    }

    override fun visit(ast: RhovasAst.Expression.Group): RhovasIr.Expression.Group {
        ast.context.firstOrNull()?.let { context.inputs.addLast(it) }
        val expression = visit(ast.expression, context.inference)
        return RhovasIr.Expression.Group(expression).also {
            it.context = ast.context
            it.context.firstOrNull()?.let { context.inputs.removeLast() }
        }
    }

    override fun visit(ast: RhovasAst.Expression.Unary): RhovasIr.Expression.Unary {
        ast.context.firstOrNull()?.let { context.inputs.addLast(it) }
        val expression = visit(ast.expression)
        val (method, _) = resolveMethod(ast, ast.expression, expression.type, ast.operator, false, listOf())
        return RhovasIr.Expression.Unary(ast.operator, expression, method).also {
            it.context = ast.context
            it.context.firstOrNull()?.let { context.inputs.removeLast() }
        }
    }

    override fun visit(ast: RhovasAst.Expression.Binary): RhovasIr.Expression.Binary {
        ast.context.firstOrNull()?.let { context.inputs.addLast(it) }
        return when (ast.operator) {
            "&&", "||" -> {
                val left = visit(ast.left, Type.BOOLEAN)
                require(left.type.isSubtypeOf(Type.BOOLEAN)) { error(
                    ast.left,
                    "Invalid binary operand.",
                    "A logical binary expression requires the left operand to be type Boolean, but received ${left.type}.",
                ) }
                val right = visit(ast.right, Type.BOOLEAN)
                require(right.type.isSubtypeOf(Type.BOOLEAN)) { error(
                    ast.right,
                    "Invalid binary operand.",
                    "A logical binary expression requires the left operand to be type Boolean, but received ${left.type}.",
                ) }
                RhovasIr.Expression.Binary(ast.operator, left, right, null, Type.BOOLEAN)
            }
            "==", "!=" -> {
                val left = visit(ast.left)
                require(left.type.methods["==", listOf(left.type)] != null) { error(ast.left,
                    "Unequatable type.",
                    "The type ${left.type} is not equatable as the method op==(${left.type}) is not defined.",
                ) }
                val right = visit(ast.right, left.type)
                RhovasIr.Expression.Binary(ast.operator, left, right, null, Type.BOOLEAN)
            }
            "===", "!==" -> {
                val left = visit(ast.left)
                val right = visit(ast.right, left.type)
                RhovasIr.Expression.Binary(ast.operator, left, right, null, Type.BOOLEAN)
            }
            "<", ">", "<=", ">=" -> {
                val left = visit(ast.left)
                val (method, arguments) = resolveMethod(ast, ast.left, left.type, "<=>", false, listOf(ast.right))
                RhovasIr.Expression.Binary(ast.operator, left, arguments[0], method, Type.BOOLEAN)
            }
            "+", "-", "*", "/" -> {
                val left = visit(ast.left)
                val (method, arguments) = resolveMethod(ast, ast.left, left.type, ast.operator, false, listOf(ast.right))
                RhovasIr.Expression.Binary(ast.operator, left, arguments[0], method, method.returns)
            }
            else -> throw AssertionError()
        }.also {
            it.context = ast.context
            it.context.firstOrNull()?.let { context.inputs.removeLast() }
        }
    }

    override fun visit(ast: RhovasAst.Expression.Access.Variable): RhovasIr.Expression.Access.Variable {
        ast.context.firstOrNull()?.let { context.inputs.addLast(it) }
        val qualifier = ast.qualifier?.let { visit(it) }
        val variable = (qualifier?.type?.base?.scope ?: context.scope).variables[ast.name] ?: throw error(
            ast,
            "Undefined variable.",
            "The variable ${ast.name} is not defined in ${qualifier?.type ?: "the current scope"}."
        )
        val initialization = context.initialization[ast.name]
        require(initialization == null || initialization.initialized) {
            initialization!!.declaration.context.firstOrNull()?.let { context.inputs.add(it) }
            error(ast,
                "Uninitialized variable.",
                "The variable ${ast.name} is not initialized.",
            )
        }
        return RhovasIr.Expression.Access.Variable(qualifier, variable).also {
            it.context = ast.context
            it.context.firstOrNull()?.let { context.inputs.removeLast() }
        }
    }

    override fun visit(ast: RhovasAst.Expression.Access.Property): RhovasIr.Expression.Access.Property {
        ast.context.firstOrNull()?.let { context.inputs.addLast(it) }
        val receiver = visit(ast.receiver)
        val receiverType = computeCoalesceReceiver(receiver, ast.coalesce)
        val bang = ast.name.endsWith('!')
        val property = receiverType.properties[ast.name.removeSuffix("!")] ?: throw error(
            ast,
            "Undefined property.",
            "The property getter ${ast.name.removeSuffix("!")}() is not defined in ${receiverType.base.name}.",
        )
        val returnsType = if (bang) {
            require(property.type.isSubtypeOf(Type.RESULT.ANY)) { error(ast,
                "Invalid bang attribute.",
                "A bang attribute requires the property getter ${ast.name.removeSuffix("!")}() to return type Result, but received ${property.type}"
            ) }
            property.type.methods["value!", listOf()]!!.returns
        } else property.type
        val type = computeCoalesceCascadeReturn(returnsType, receiver.type, ast.coalesce, false)
        return RhovasIr.Expression.Access.Property(receiver, property, bang, ast.coalesce, type).also {
            it.context = ast.context
            it.context.firstOrNull()?.let { context.inputs.removeLast() }
        }
    }

    override fun visit(ast: RhovasAst.Expression.Access.Index): RhovasIr.Expression.Access.Index {
        ast.context.firstOrNull()?.let { context.inputs.addLast(it) }
        val receiver = visit(ast.receiver)
        val (method, arguments) = resolveMethod(ast, ast.receiver, receiver.type, "[]", false, ast.arguments)
        return RhovasIr.Expression.Access.Index(receiver, method, arguments).also {
            it.context = ast.context
            it.context.firstOrNull()?.let { context.inputs.removeLast() }
        }
    }

    override fun visit(ast: RhovasAst.Expression.Invoke.Constructor): RhovasIr {
        ast.context.firstOrNull()?.let { context.inputs.addLast(it) }
        val type = visit(ast.type).type
        require(type is Type.Reference) { error(
            ast,
            "Unconstructable type.",
            "The type ${type} cannot be constructed (only reference types)."
        ) }
        val arguments = mutableListOf<RhovasIr.Expression>()
        val function = resolveFunction("constructor", ast, type, "", false, ast.arguments.size) {
            ast.arguments[it.first] to visit(ast.arguments[it.first], it.second).also { arguments.add(it) }.type
        }
        return RhovasIr.Expression.Invoke.Constructor(type as Type.Reference, function, arguments, function.returns).also {
            it.context = ast.context
            it.context.firstOrNull()?.let { context.inputs.removeLast() }
        }
    }

    override fun visit(ast: RhovasAst.Expression.Invoke.Function): RhovasIr.Expression.Invoke.Function {
        ast.context.firstOrNull()?.let { context.inputs.addLast(it) }
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
        return RhovasIr.Expression.Invoke.Function(qualifier, function, bang, arguments, returns).also {
            it.context = ast.context
            it.context.firstOrNull()?.let { context.inputs.removeLast() }
        }
    }

    override fun visit(ast: RhovasAst.Expression.Invoke.Method): RhovasIr.Expression.Invoke.Method {
        ast.context.firstOrNull()?.let { context.inputs.addLast(it) }
        val receiver = visit(ast.receiver)
        val receiverType = computeCoalesceReceiver(receiver, ast.coalesce)
        val (method, arguments) = resolveMethod(ast, ast.receiver, receiverType, ast.name, true, ast.arguments)
        val bang = ast.name.endsWith('!')
        val returns = computeBangReturn(method.function, bang) { error(ast,
            "Invalid bang attribute.",
            "A bang attribute requires the method ${ast.name} to return type Result, but received ${method.returns}."
        ) }
        val type = computeCoalesceCascadeReturn(returns, receiver.type, ast.coalesce, ast.cascade)
        return RhovasIr.Expression.Invoke.Method(receiver, method, bang, ast.coalesce, ast.cascade, arguments, type).also {
            it.context = ast.context
            it.context.firstOrNull()?.let { context.inputs.removeLast() }
        }
    }

    override fun visit(ast: RhovasAst.Expression.Invoke.Pipeline): RhovasIr.Expression.Invoke.Pipeline {
        ast.context.firstOrNull()?.let { context.inputs.addLast(it) }
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
        return RhovasIr.Expression.Invoke.Pipeline(receiver, qualifier, function, bang, ast.coalesce, ast.cascade, arguments, type).also {
            it.context = ast.context
            it.context.firstOrNull()?.let { context.inputs.removeLast() }
        }
    }

    private fun computeCoalesceReceiver(receiver: RhovasIr.Expression, coalesce: Boolean): Type {
        return if (coalesce) {
            require(receiver.type.isSubtypeOf(Type.RESULT.ANY)) { error(
                "Invalid coalesce.",
                "Coalescing requires the receiver to be type Result, but received ${receiver.type}.",
                receiver.context.firstOrNull(),
            ) }
            receiver.type.methods["value!", listOf()]!!.returns
        } else {
            receiver.type
        }
    }

    private fun computeBangReturn(function: Function, bang: Boolean, error: () -> AnalyzeException): Type {
        return when {
            bang && function.throws.isEmpty() -> {
                require(function.returns.isSubtypeOf(Type.RESULT.ANY), error)
                function.returns.methods["value!", listOf()]!!.returns
            }
            !bang && function.throws.isNotEmpty() -> Type.RESULT[function.returns, function.throws.singleOrNull() ?: Type.EXCEPTION]
            else -> function.returns
        }
    }

    private fun computeCoalesceCascadeReturn(returns: Type, receiver: Type, coalesce: Boolean, cascade: Boolean): Type {
        return when {
            cascade -> receiver
            coalesce -> when {
                returns.isSubtypeOf(Type.RESULT.ANY) -> returns
                receiver.isSubtypeOf(Type.NULLABLE.ANY) -> Type.NULLABLE[returns]
                else -> Type.RESULT[returns, mutableMapOf<String, Type>().also { receiver.isSubtypeOf(Type.RESULT[Type.DYNAMIC, Type.Generic("E", Type.ANY)], it) }["E"]!!]
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
        generator: (Pair<Int, Type?>) -> Pair<RhovasAst.Expression, Type>,
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
            val inference = filtered.takeIf { it.size == 1 }?.let { it[0].first.parameters.getOrNull(i)?.type?.bind(it[0].second) }
            val (ast, type) = generator(Pair(i, inference)).also { arguments.add(it.second) }
            filtered.retainAll { type.isSubtypeOf(it.first.parameters[i].type, it.second) }
            require(filtered.isNotEmpty()) { when (candidates.size) {
                1 -> error(ast,
                    "Invalid argument.",
                    "The ${term} ${descriptor} requires argument ${i} to be type ${candidates[0].first.parameters[i].type.bind(candidates[0].second)} but received ${arguments[i]}.",
                )
                else -> error(ast,
                    "Unresolved method.",
                    "The ${term} ${descriptor} could not be resolved to one of the available overloads below with arguments (${arguments.joinToString()}):\n${candidates.map { c -> "\n - ${c.first.name}(${c.first.parameters.joinToString { "${it.type}" }}" }}",
                )
            } }
        }
        val function = (qualifier?.functions?.get(filtered.first().first.name, arguments) ?: context.scope.functions[filtered.first().first.name, arguments])!!
        function.throws.forEach { exception ->
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

    override fun visit(ast: RhovasAst.Expression.Invoke.Macro): RhovasIr.Expression {
        ast.context.firstOrNull()?.let { context.inputs.addLast(it) }
        if (ast.dsl == null) {
            //val arguments = ast.arguments.map { visit(it) }
            throw error(
                ast,
                "Unsupported Macro",
                "Macros without DSLs are not currently supported.",
            )
        } else {
            require(ast.arguments.isEmpty()) { error(
                ast,
                "Invalid DSL arguments.",
                "DSLs with arguments are not currently supported.",
            ) }
            val source = ast.dsl as? DslAst.Source ?: throw error(
                ast,
                "Invalid DSL AST.",
                "The AST of type " + ast.dsl + " is not currently supported.",
            )
            val function = context.scope.functions[ast.name, listOf(Type.LIST[Type.STRING], Type.LIST[Type.DYNAMIC])] ?: throw error(
                ast,
                "Undefined DSL transformer.",
                "The DSL ${ast.name} requires a transformer function ${ast.name}(List<String>, List<Dynamic>).",
            )
            val literals = RhovasIr.Expression.Literal.List(
                source.literals.map { RhovasIr.Expression.Literal.String(listOf(it), listOf(), Type.STRING) },
                Type.LIST[Type.STRING],
            )
            val arguments = RhovasIr.Expression.Literal.List(
                source.arguments.map { visit(it as RhovasAst.Expression) },
                Type.LIST[Type.DYNAMIC],
            )
            return RhovasIr.Expression.Invoke.Function(null, function, ast.name.endsWith('!'), listOf(literals, arguments), function.returns).also {
                it.context = ast.context
                it.context.firstOrNull()?.let { context.inputs.removeLast() }
            }
        }
    }

    override fun visit(ast: RhovasAst.Expression.Lambda): RhovasIr.Expression.Lambda {
        ast.context.firstOrNull()?.let { context.inputs.addLast(it) }
        //TODO(#2): Forward thrown exceptions from context into declaration
        val (inferenceParameters, inferenceReturns, inferenceThrows) = if (context.inference?.base == Type.LAMBDA.ANY.base) {
            val generics = (context.inference as Type.Reference).generics
            val arguments = when(generics[0]) {
                is Type.Generic -> (generics[0] as Type.Generic).bound
                is Type.Variant -> (generics[0] as Type.Variant).upper
                else -> generics[0]
            }?.let { (it as? Type.Reference)?.generics?.firstOrNull() as? Type.Tuple? }
            Triple(arguments, generics[1], generics[2])
        } else Triple(null, null, null)
        val parameters = ast.parameters.withIndex().map {
            val type = it.value.second?.let { visit(it).type }
                ?: inferenceParameters?.elements?.getOrNull(it.index)?.type
                ?: Type.DYNAMIC
            Variable.Declaration(it.value.first, type, false)
        }
        val returns = inferenceReturns?.bind(mapOf("R" to Type.DYNAMIC)) ?: Type.DYNAMIC
        val throws = listOfNotNull(inferenceThrows?.bind(mapOf("E" to Type.EXCEPTION)))
        val function = Function.Declaration("lambda", listOf(), parameters, returns, throws)
        return analyze(context.child().with(
            FunctionContext(function),
            ExceptionContext(function.throws.toMutableSet())
        )) {
            if (parameters.isNotEmpty()) {
                parameters.forEach { (context.scope as Scope.Declaration).variables.define(it) }
            } else {
                val type = inferenceParameters?.takeIf { it.elements.size == 1 }?.let { it.elements[0].type }
                    ?: inferenceParameters?.let { Type.TUPLE[it] }
                    ?: Type.DYNAMIC
                (context.scope as Scope.Declaration).variables.define(Variable.Declaration("val", type, false))
            }
            val body = visit(ast.body)
            require(body.type.isSubtypeOf(context.function!!.returns) || context.jumps.contains("")) { error(
                ast,
                "Invalid return value type.",
                "The enclosing function ${context.function!!.name}/${context.function!!.parameters.size} requires the return value to be type ${context.function!!.returns}, but received ${body.type}.",
            ) }
            val type = Type.LAMBDA[parameters.takeIf { it.isNotEmpty() }?.let { Type.TUPLE[Type.Tuple(it)] } ?: Type.DYNAMIC, returns, Type.DYNAMIC]
            RhovasIr.Expression.Lambda(parameters, body, type).also {
                it.context = ast.context
                it.context.firstOrNull()?.let { context.inputs.removeLast() }
            }
        }
    }

    private fun visit(ast: RhovasAst.Pattern): RhovasIr.Pattern {
        return super.visit(ast) as RhovasIr.Pattern
    }

    override fun visit(ast: RhovasAst.Pattern.Variable): RhovasIr.Pattern.Variable {
        ast.context.firstOrNull()?.let { context.inputs.addLast(it) }
        require(!context.pattern.bindings.containsKey(ast.name)) { error(
            ast,
            "Redefined pattern binding",
            "The identifier ${ast.name} is already bound in this pattern.",
        ) }
        val variable = if (ast.name != "_") {
            context.pattern.bindings[ast.name] = context.pattern.type
            Variable.Declaration(ast.name, context.pattern.type, false)
        } else null
        return RhovasIr.Pattern.Variable(variable).also {
            it.context = ast.context
            it.context.firstOrNull()?.let { context.inputs.removeLast() }
        }
    }

    override fun visit(ast: RhovasAst.Pattern.Value): RhovasIr.Pattern.Value {
        val value = visit(ast.value, context.pattern.type)
        require(value.type.isSubtypeOf(context.pattern.type)) { error(
            ast,
            "Unmatchable pattern type",
            "This pattern is within a context that requires type ${context.pattern.type}, but received ${value.type}.",
        ) }
        return RhovasIr.Pattern.Value(value).also {
            it.context = ast.context
        }
    }

    override fun visit(ast: RhovasAst.Pattern.Predicate): RhovasIr.Pattern.Predicate {
        ast.context.firstOrNull()?.let { context.inputs.addLast(it) }
        val existing = context.pattern.bindings.toMutableMap()
        val (pattern, predicate) = analyze(context.with(PatternContext(context.pattern.type, mutableMapOf()))) {
            val pattern = visit(ast.pattern)
            (context.scope as Scope.Declaration).variables.define(Variable.Declaration("val", context.pattern.type, false))
            context.pattern.bindings
                .filterKeys { !existing.containsKey(it) }
                .forEach { (context.scope as Scope.Declaration).variables.define(Variable.Declaration(it.key, it.value, false)) }
            val predicate = visit(ast.predicate, Type.BOOLEAN)
            Pair(pattern, predicate)
        }
        require(predicate.type.isSubtypeOf(Type.BOOLEAN)) { error(
            ast.predicate,
            "Invalid pattern predicate type.",
            "A predicate pattern requires the predicate to be type Boolean, but received ${predicate.type}.",
        ) }
        return RhovasIr.Pattern.Predicate(pattern, predicate).also {
            it.context = ast.context
            it.context.firstOrNull()?.let { context.inputs.removeLast() }
        }
    }

    override fun visit(ast: RhovasAst.Pattern.OrderedDestructure): RhovasIr.Pattern.OrderedDestructure {
        ast.context.firstOrNull()?.let { context.inputs.addLast(it) }
        require(context.pattern.type.isSupertypeOf(Type.LIST.ANY)) { error(
            ast,
            "Unmatchable pattern type",
            "This pattern is within a context that requires type ${context.pattern.type}, but received List.",
        ) }
        val type = when {
            context.pattern.type.isSubtypeOf(Type.LIST.ANY) -> context.pattern.type.methods["get", listOf(Type.INTEGER)]!!.returns
            else -> Type.DYNAMIC
        }
        var vararg = false
        val patterns = ast.patterns.withIndex().map {
            if (it.value is RhovasAst.Pattern.VarargDestructure) {
                require(!vararg) { error(
                    it.value,
                    "Invalid multiple varargs.",
                    "An ordered destructure requires no more than one vararg pattern.",
                ) }
                vararg = true
                val ast = it.value as RhovasAst.Pattern.VarargDestructure
                val pattern = ast.pattern?.let {
                    val existing = context.pattern.bindings.toMutableMap()
                    analyze(context.with(PatternContext(type, context.pattern.bindings))) {
                        val pattern = visit(it)
                        context.pattern.bindings
                            .filterKeys { !existing.containsKey(it) }
                            .forEach { context.pattern.bindings[it.key] = Type.LIST[it.value] }
                        pattern
                    }
                }
                RhovasIr.Pattern.VarargDestructure(pattern, ast.operator, Type.LIST[type]).also {
                    it.context = ast.context
                }
            } else {
                analyze(context.with(PatternContext(type, context.pattern.bindings))) {
                    visit(it.value)
                }
            }
        }
        return RhovasIr.Pattern.OrderedDestructure(patterns, Type.LIST[type]).also {
            it.context = ast.context
            it.context.firstOrNull()?.let { context.inputs.removeLast() }
        }
    }

    override fun visit(ast: RhovasAst.Pattern.NamedDestructure): RhovasIr.Pattern.NamedDestructure {
        ast.context.firstOrNull()?.let { context.inputs.addLast(it) }
        require(context.pattern.type.isSupertypeOf(Type.OBJECT)) { error(
            ast,
            "Unmatchable pattern type",
            "This pattern is within a context that requires type ${context.pattern.type}, but received List.",
        ) }
        var vararg = false
        val patterns = ast.patterns.withIndex().map {
            if (it.value.second is RhovasAst.Pattern.VarargDestructure) {
                require(!vararg) { error(
                    it.value.second,
                    "Invalid multiple varargs.",
                    "A named destructure requires no more than one vararg pattern.",
                ) }
                vararg = true
                val ast = it.value.second as RhovasAst.Pattern.VarargDestructure
                val pattern = ast.pattern?.let {
                    val existing = context.pattern.bindings.toMutableMap()
                    //TODO(#14): Struct type validation
                    analyze(context.with(PatternContext(Type.DYNAMIC, context.pattern.bindings))) {
                        val pattern = visit(it)
                        context.pattern.bindings
                            .filterKeys { !existing.containsKey(it) }
                            .forEach { context.pattern.bindings[it.key] = Type.OBJECT }
                        pattern
                    }
                }
                //TODO(#14): Struct type bindings
                Pair(null, RhovasIr.Pattern.VarargDestructure(pattern, ast.operator, Type.OBJECT).also {
                    it.context = ast.context
                })
            } else {
                val key = it.value.first
                    ?: (it.value.second as? RhovasAst.Pattern.Variable)?.name
                    ?: throw error(
                        it.value.second,
                        "Missing pattern key",
                        "This pattern requires a key to be used within a named destructure.",
                    )
                //TODO(#14): Struct type validation
                val pattern = analyze(context.with(PatternContext(Type.DYNAMIC, context.pattern.bindings))) {
                    visit(it.value.second)
                }
                //TODO(#14): Struct type bindings
                context.pattern.bindings[key] = pattern.type
                Pair(key, pattern)
            }
        }
        return RhovasIr.Pattern.NamedDestructure(patterns, Type.OBJECT).also {
            it.context = ast.context
            it.context.firstOrNull()?.let { context.inputs.removeLast() }
        }
    }

    override fun visit(ast: RhovasAst.Pattern.TypedDestructure): RhovasIr.Pattern.TypedDestructure {
        val type = visit(ast.type)
        require(type.type.isSubtypeOf(context.pattern.type)) { error(
            ast,
            "Unmatchable pattern type",
            "This pattern is within a context that requires type ${context.pattern.type}, but received ${type.type}.",
        ) }
        val pattern = ast.pattern?.let {
            analyze(context.with(PatternContext(type.type, context.pattern.bindings))) {
                visit(it)
            }
        }
        return RhovasIr.Pattern.TypedDestructure(type.type, pattern).also {
            it.context = ast.context
        }
    }

    override fun visit(ast: RhovasAst.Pattern.VarargDestructure): RhovasIr.Pattern.VarargDestructure {
        throw AssertionError()
    }

    override fun visit(ast: RhovasAst.Type): RhovasIr.Type {
        ast.context.firstOrNull()?.let { context.inputs.addLast(it) }
        var type: Type = context.scope.types[ast.path.first()] ?: throw error(
            ast,
            "Undefined type.",
            "The type ${ast.path.first()} is not defined in the current scope."
        )
        ast.path.drop(1).forEach {
            type = type.base.scope.types[it] ?: throw error(
                ast,
                "Undefined type.",
                "The type ${it} is not defined in ${type}."
            )
        }
        if (ast.generics != null) {
            require(type is Type.Reference) { error(
                ast,
                "Invalid generic parameters.",
                "Generic type require a reference type, but received a base type of Type.${type::class.simpleName} (${type}).",
            ) }
            val generics = ast.generics.map { visit(it).type }
            require(type.base.generics.size == generics.size) { error(
                ast,
                "Invalid generic parameters.",
                "The type ${type.base.name} requires ${type.base.generics.size} generic parameters, but received ${generics.size}.",
            ) }
            for (i in generics.indices) {
                require(generics[i].isSubtypeOf(type.base.generics[i].bound)) { error(
                    ast.generics[i],
                    "Invalid generic parameter.",
                    "The type ${type.base.name} requires generic parameter ${i} to be type ${type.base.generics[i].bound}, but received ${generics[i]}.",
                ) }
            }
            type = Type.Reference(type.base, generics)
        }
        return RhovasIr.Type(type).also {
            it.context = ast.context
            it.context.firstOrNull()?.let { context.inputs.removeLast() }
        }
    }

    /**
     * Analyzer phase to forward-declare types that may be referenced during
     * type definition.
     */
    private inner class DeclarePhase {

        fun visit(ast: RhovasAst.Component) {
            when (ast) {
                is RhovasAst.Component.Struct -> visit(ast)
                is RhovasAst.Component.Class -> visit(ast)
            }
        }

        fun visit(ast: RhovasAst.Component.Struct) {
            ast.context.firstOrNull()?.let { context.inputs.addLast(it) }
            require(!context.scope.types.isDefined(ast.name, true)) { error(
                ast,
                "Redefined type.",
                "The type ${ast.name} is already defined in this scope.",
            ) }
            context.scope.types.define(Type.Base(ast.name, Scope.Definition(null)).reference)
            ast.context.firstOrNull()?.let { context.inputs.removeLast() }
        }

        fun visit(ast: RhovasAst.Component.Class) {
            ast.context.firstOrNull()?.let { context.inputs.addLast(it) }
            require(!context.scope.types.isDefined(ast.name, true)) { error(
                ast,
                "Redefined type.",
                "The type ${ast.name} is already defined in this scope.",
            ) }
            context.scope.types.define(Type.Base(ast.name, Scope.Definition(null)).reference)
            ast.context.firstOrNull()?.let { context.inputs.removeLast() }
        }

    }

    /**
     * Analyzer phase to forward-define types to ensure full type information
     * is available during analysis.
     */
    private inner class DefinePhase {

        fun visit(ast: RhovasAst.Component.Struct): RhovasIr.DefinitionPhase.Component.Struct {
            ast.context.firstOrNull()?.let { context.inputs.addLast(it) }
            val type = context.scope.types[ast.name]!!
            val members = ast.members.map { visit(it, type) }.toMutableList()
            val fields = members.filterIsInstance<RhovasIr.DefinitionPhase.Member.Property>().associateBy { it.getter.name }
            type.base.inherit(Type.STRUCT[Type.Struct(fields.mapValues { Variable.Declaration(it.key, it.value.getter.returns, it.value.setter != null) })])
            type.base.scope.functions.define(Function.Definition(Function.Declaration("", listOf(), listOf(Variable.Declaration("fields", Type.STRUCT[Type.Struct(fields.filter { it.value.ast.value == null }.mapValues { Variable.Declaration(it.key, it.value.getter.returns, it.value.setter != null) })], false)), type, listOf())))
            return RhovasIr.DefinitionPhase.Component.Struct(ast, members).also {
                ast.context.firstOrNull()?.let { context.inputs.removeLast() }
            }
        }

        fun visit(ast: RhovasAst.Component.Class): RhovasIr.DefinitionPhase.Component.Class {
            ast.context.firstOrNull()?.let { context.inputs.addLast(it) }
            val clazz = context.scope.types[ast.name]!!
            val members = ast.members.map { visit(it, clazz) }.toMutableList()
            return RhovasIr.DefinitionPhase.Component.Class(ast, members).also {
                ast.context.firstOrNull()?.let { context.inputs.removeLast() }
            }
        }

        fun visit(ast: RhovasAst.Member, component: Type): RhovasIr.DefinitionPhase.Member {
            return when (ast) {
                is RhovasAst.Member.Property -> visit(ast, component)
                is RhovasAst.Member.Initializer -> visit(ast, component)
                is RhovasAst.Member.Method -> visit(ast, component)
            }
        }

        fun visit(ast: RhovasAst.Member.Property, component: Type): RhovasIr.DefinitionPhase.Member.Property {
            ast.context.firstOrNull()?.let { context.inputs.addLast(it) }
            require(component.properties[ast.name] == null) { error(
                ast,
                "Redefined property.",
                "The property ${ast.name} is already defined in ${component.base.name}.",
            ) }
            val type = ast.type.let { visit(it).type }
            val getter = Function.Definition(Function.Declaration(ast.name, listOf(), listOf(Variable.Declaration("this", component, false)), type, listOf()))
            val setter = if (ast.mutable) Function.Definition(Function.Declaration(ast.name, listOf(), listOf(Variable.Declaration("this", component, false), Variable.Declaration("value", type, false)), Type.VOID, listOf())) else null
            component.base.scope.functions.define(getter)
            setter?.let { component.base.scope.functions.define(it) }
            return RhovasIr.DefinitionPhase.Member.Property(ast, getter, setter).also {
                ast.context.firstOrNull()?.let { context.inputs.removeLast() }
            }
        }

        fun visit(ast: RhovasAst.Member.Initializer, component: Type): RhovasIr.DefinitionPhase.Member.Initializer {
            ast.context.firstOrNull()?.let { context.inputs.addLast(it) }
            val parameters = ast.parameters.mapIndexed { index, parameter ->
                val type = parameter.second?.let { visit(it).type } ?: component.takeIf { index == 0 } ?: throw error(ast,
                    "Undefined parameter type.",
                    "The initializer init/${ast.parameters.size} requires parameter ${index} to have an defined type.",
                )
                Variable.Declaration(parameter.first, type, false)
            }
            val returns = ast.returns?.let { visit(it).type } ?: component
            val throws = ast.throws.map { visit(it).type }
            val initializer = Function.Definition(Function.Declaration("", listOf(), parameters, returns, throws))
            require(component.base.scope.functions["", ast.parameters.size, true].all { it.isDisjointWith(initializer.declaration) }) { error(
                ast,
                "Redefined initializer.",
                "The initializer init/${ast.parameters.size} overlaps with an existing function in ${component.base.name}.",
            ) }
            component.base.scope.functions.define(initializer)
            return RhovasIr.DefinitionPhase.Member.Initializer(ast, initializer).also {
                ast.context.firstOrNull()?.let { context.inputs.removeLast() }
            }
        }

        fun visit(ast: RhovasAst.Member.Method, component: Type): RhovasIr.DefinitionPhase.Member.Method {
            val function = visit(ast.function, component)
            return RhovasIr.DefinitionPhase.Member.Method(ast, function)
        }

        fun visit(ast: RhovasAst.Statement.Declaration.Function, component: Type? = null): RhovasIr.DefinitionPhase.Function {
            ast.context.firstOrNull()?.let { context.inputs.addLast(it) }
            val scope = component?.base?.scope ?: context.scope
            return analyze {
                val generics = ast.generics.map { Type.Generic(it.first, it.second?.let { visit(it).type } ?: Type.ANY) }
                generics.forEach { context.scope.types.define(it, it.name) }
                val parameters = ast.parameters.mapIndexed { index, parameter ->
                    val type = parameter.second?.let { visit(it).type } ?: component.takeIf { index == 0 } ?: throw error(ast,
                        "Undefined parameter type.",
                        "The function ${ast.name}/${ast.parameters.size} requires parameter ${index} to have an explicit type.",
                    )
                    Variable.Declaration(parameter.first, type, false)
                }
                val returns = ast.returns?.let { visit(it).type } ?: Type.VOID
                val throws = ast.throws.map { visit(it).type }
                val declaration = Function.Declaration(ast.name, generics, parameters, returns, throws)
                require(scope.functions[ast.name, ast.parameters.size, true].all { it.isDisjointWith(declaration) }) { error(
                    ast,
                    "Redefined function.",
                    "The function ${ast.name}/${ast.parameters.size} overlaps with an existing function in ${component?.base?.name ?: "this scope"}.",
                ) }
                val method = when (scope) {
                    is Scope.Declaration -> declaration.also { scope.functions.define(it) }
                    is Scope.Definition -> Function.Definition(declaration).also { scope.functions.define(it) }
                }
                RhovasIr.DefinitionPhase.Function(ast, method).also {
                    ast.context.firstOrNull()?.let { context.inputs.removeLast() }
                }
            }
        }

    }

}
