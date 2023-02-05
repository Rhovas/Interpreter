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
        InitializationContext(mutableMapOf()),
        ScopeContext(scope),
        FunctionContext(null),
        LabelContext(mutableSetOf()),
        JumpContext(mutableSetOf()),
        ExceptionContext(mutableSetOf()),
    ).associateBy { it::class.simpleName!! })),
    RhovasIr.DefinitionPhase.Visitor<RhovasIr> {

    private val declare = DeclarePhase()
    private val define = DefinePhase()

    private val Context.scope get() = this[ScopeContext::class]
    private val Context.initialization get() = this[InitializationContext::class]
    private val Context.function get() = this[FunctionContext::class]
    private val Context.labels get() = this[LabelContext::class]
    private val Context.jumps get() = this[JumpContext::class]
    private val Context.exceptions get() = this[ExceptionContext::class]
    private val Context.pattern get() = this[PatternContext::class]

    data class ScopeContext(
        val scope: Scope<out Variable, out Function>,
    ) : Context.Item<Scope<out Variable, out Function>>(scope) {

        override fun child(): ScopeContext {
            return ScopeContext(Scope.Declaration(scope))
        }

        override fun merge(children: List<Scope<out Variable, out Function>>) {}

    }

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

        override fun merge(children: List<MutableMap<String, Data>>) {
            for ((variable, data) in variables) {
                if (!data.initialized) {
                    when (children.count { it[variable]!!.initialized }) {
                        0 -> {}
                        children.size -> variables[variable] = Data(true, data.declaration, children.flatMap { it[variable]!!.context }.toMutableList())
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

    }

    data class FunctionContext(
        val function: Function?
    ) : Context.Item<Function?>(function) {

        override fun child(): FunctionContext {
            return this
        }

        override fun merge(children: List<Function?>) {}

    }

    data class LabelContext(
        val labels: MutableSet<String?>,
    ) : Context.Item<MutableSet<String?>>(labels) {

        override fun child(): LabelContext {
            return LabelContext(labels.toMutableSet())
        }

        override fun merge(children: List<MutableSet<String?>>) {}

    }

    data class JumpContext(
        val jumps: MutableSet<String?>,
    ) : Context.Item<MutableSet<String?>>(jumps) {

        override fun child(): Context.Item<MutableSet<String?>> {
            return JumpContext(mutableSetOf())
        }

        override fun merge(children: List<MutableSet<String?>>) {
            if (children.any { it.isEmpty() }) {
                jumps.clear()
            } else {
                children.forEach { jumps.addAll(it) }
            }
        }

    }

    data class ExceptionContext(
        val exceptions: MutableSet<Type>,
        //TODO: Unthrown exceptions
    ) : Context.Item<MutableSet<Type>>(exceptions) {

        override fun child(): Context.Item<MutableSet<Type>> {
            return ExceptionContext(exceptions.toMutableSet())
        }

        override fun merge(children: List<MutableSet<Type>>) {}

    }

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
        val value = ir.ast.value?.let { visit(it) }
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
            FunctionContext(ir.function.declaration.copy(returns = Library.TYPES["Void"]!!)), //TODO: Hack to prevent manual returning
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
        val initializer = visit(ast.initializer) as RhovasIr.Expression.Literal.Object
        //TODO: Validate available fields
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
        //TODO: Also validate post-macro IR
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
        val value = ast.value?.let { visit(it) }
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
            require(ir.function.returns.isSubtypeOf(Library.TYPES["Void"]!!) || context.jumps.contains("")) { error(
                ir.ast,
                "Missing return value.",
                "The function ${ir.ast.name}/${ir.ast.parameters.size} requires a return value.",
            ) }
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
                val value = visit(ast.value)
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
                val value = visit(ast.value)
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
                val arguments = ast.receiver.arguments.map { visit(it) }
                val value = visit(ast.value)
                val method = receiver.type.methods["[]=", arguments.map { it.type } + listOf(value.type)] ?: throw error(
                    ast,
                    "Unresolved method.",
                    "The signature []=(${(arguments.map { it.type } + listOf(value.type)).joinToString(", ")}) could not be resolved to a method in ${receiver.type.base.name}.",
                )
                RhovasIr.Statement.Assignment.Index(receiver, method, arguments, value).also {
                    it.context = ast.context
                    it.context.firstOrNull()?.let { context.inputs.removeLast() }
                }
            }
            else -> throw AssertionError()
        }
    }

    override fun visit(ast: RhovasAst.Statement.If): RhovasIr.Statement.If {
        ast.context.firstOrNull()?.let { context.inputs.addLast(it) }
        val condition = visit(ast.condition)
        require(condition.type.isSubtypeOf(Library.TYPES["Boolean"]!!)) { error(
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
            val condition = visit(ast)
            require(condition.type.isSubtypeOf(Library.TYPES["Boolean"]!!)) { error(
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
        //TODO: Typecheck patterns
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
        val argument = visit(ast.argument)
        require(argument.type.isSubtypeOf(Library.TYPES["List"]!!)) { error(
            ast.argument,
            "Invalid for loop argument type.",
            "A for loop requires the argument to be type List, but received ${argument.type}.",
        ) }
        val type = argument.type.methods["get", listOf(Library.TYPES["Integer"]!!)]!!.returns
        val variable = Variable.Declaration(ast.name, type, false)
        return analyze {
            (context.scope as Scope.Declaration).variables.define(variable)
            context.labels.add(null)
            val block = visit(ast.block)
            //TODO: Validate jump context
            context.jumps.clear()
            RhovasIr.Statement.For(variable, argument, block).also {
                it.context = ast.context
                it.context.firstOrNull()?.let { context.inputs.removeLast() }
            }
        }
    }

    override fun visit(ast: RhovasAst.Statement.While): RhovasIr.Statement.While {
        ast.context.firstOrNull()?.let { context.inputs.addLast(it) }
        val condition = visit(ast.condition)
        require(condition.type.isSubtypeOf(Library.TYPES["Boolean"]!!)) { error(
            ast.condition,
            "Invalid while condition type.",
            "An while statement requires the condition to be type Boolean, but received ${condition.type}.",
        ) }
        return analyze {
            context.labels.add(null)
            val block = visit(ast.block)
            context.jumps.clear()
            //TODO: Validate jump context
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
            require(type.isSubtypeOf(Library.TYPES["Exception"]!!)) { error(
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
            //TODO: Include finally information in invalid exception error message
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
            //TODO: Validate jump locations (constant conditions / dependent types)
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
        val value = ast.value?.let { visit(it) }
        require((value?.type ?: Library.TYPES["Void"]!!).isSubtypeOf(context.function!!.returns)) { error(
            ast,
            "Invalid return value type.",
            "The enclosing function ${context.function!!.name}/${context.function!!.parameters.size} requires the return value to be type ${context.function!!.returns}, but received ${value?.type ?: Library.TYPES["Void"]!!}.",
        ) }
        context.jumps.add("")
        return RhovasIr.Statement.Return(value).also {
            it.context = ast.context
            it.context.firstOrNull()?.let { context.inputs.removeLast() }
        }
    }

    override fun visit(ast: RhovasAst.Statement.Throw): RhovasIr.Statement.Throw {
        ast.context.firstOrNull()?.let { context.inputs.addLast(it) }
        val exception = visit(ast.exception)
        require(exception.type.isSubtypeOf(Library.TYPES["Exception"]!!)) { error(
            ast.exception,
            "Invalid throw expression type.",
            "An throw statement requires the expression to be type Exception, but received ${exception.type}.",
        ) }
        require(context.exceptions.any { exception.type.isSubtypeOf(it) }) { error(
            ast.exception,
            "Uncaught exception.",
            "An exception is thrown of type ${exception.type}, but this exception is never caught or declared.",
        ) }
        return RhovasIr.Statement.Throw(exception).also {
            it.context = ast.context
            it.context.firstOrNull()?.let { context.inputs.removeLast() }
        }
    }

    override fun visit(ast: RhovasAst.Statement.Assert): RhovasIr.Statement.Assert {
        ast.context.firstOrNull()?.let { context.inputs.addLast(it) }
        val condition = visit(ast.condition)
        require(condition.type.isSubtypeOf(Library.TYPES["Boolean"]!!)) { error(
            ast.condition,
            "Invalid assert condition type.",
            "An assert statement requires the condition to be type Boolean, but received ${condition.type}.",
        ) }
        val message = ast.message?.let { visit(it) }
        require(message == null || message.type.isSubtypeOf(Library.TYPES["String"]!!)) { error(
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
        val condition = visit(ast.condition)
        require(condition.type.isSubtypeOf(Library.TYPES["Boolean"]!!)) { error(
            ast.condition,
            "Invalid require condition type.",
            "A require statement requires the condition to be type Boolean, but received ${condition.type}.",
        ) }
        val message = ast.message?.let { visit(it) }
        require(message == null || message.type.isSubtypeOf(Library.TYPES["String"]!!)) { error(
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
        val condition = visit(ast.condition)
        require(condition.type.isSubtypeOf(Library.TYPES["Boolean"]!!)) { error(
            ast.condition,
            "Invalid ensure condition type.",
            "An ensure statement requires the condition to be type Boolean, but received ${condition.type}.",
        ) }
        val message = ast.message?.let { visit(it) }
        require(message == null || message.type.isSubtypeOf(Library.TYPES["String"]!!)) { error(
            ast.message!!,
            "Invalid ensure message type.",
            "An ensure statement requires the message to be type String, but received ${message!!.type}.",
        ) }
        return RhovasIr.Statement.Ensure(condition, message).also {
            it.context = ast.context
            it.context.firstOrNull()?.let { context.inputs.removeLast() }
        }
    }

    private fun visit(ast: RhovasAst.Expression): RhovasIr.Expression {
        return super.visit(ast) as RhovasIr.Expression
    }

    override fun visit(ast: RhovasAst.Expression.Block): RhovasIr.Expression.Block {
        ast.context.firstOrNull()?.let { context.inputs.addLast(it) }
        val statements = ast.statements.withIndex().map {
            //TODO: This index check is wrong, also seems like continue isn't working right
            require(it.index == ast.statements.lastIndex || context.jumps.isEmpty()) { error(
                ast,
                "Unreachable statement.",
                "The previous statement changes control flow to always jump past this statement.",
            ) }
            visit(it.value)
        }
        val expression = ast.expression?.let {
            require(context.jumps.isEmpty()) { error(
                ast,
                "Unreachable statement.",
                "The previous statement changes control flow to always jump past this statement.",
            ) }
            visit(it)
        }
        val type = expression?.type ?: Library.TYPES["Void"]!!
        return RhovasIr.Expression.Block(statements, expression, type).also {
            it.context = ast.context
            it.context.firstOrNull()?.let { context.inputs.removeLast() }
        }
    }

    override fun visit(ast: RhovasAst.Expression.Literal.Scalar): RhovasIr {
        val type = when (ast.value) {
            null -> Library.TYPES["Null"]!!
            is Boolean -> Library.TYPES["Boolean"]!!
            is BigInteger -> Library.TYPES["Integer"]!!
            is BigDecimal -> Library.TYPES["Decimal"]!!
            is RhovasAst.Atom -> Library.TYPES["Atom"]!!
            else -> throw AssertionError()
        }
        return RhovasIr.Expression.Literal.Scalar(ast.value, type).also {
            it.context = ast.context
        }
    }

    override fun visit(ast: RhovasAst.Expression.Literal.String): RhovasIr {
        val arguments = ast.arguments.map { visit(it) }
        val type = Library.TYPES["String"]!!
        return RhovasIr.Expression.Literal.String(ast.literals, arguments, type).also {
            it.context = ast.context
        }
    }

    override fun visit(ast: RhovasAst.Expression.Literal.List): RhovasIr {
        ast.context.firstOrNull()?.let { context.inputs.addLast(it) }
        val elements = ast.elements.map { visit(it) }
        val type = Type.Reference(Library.TYPES["List"]!!.base, listOf(Library.TYPES["Dynamic"]!!))
        return RhovasIr.Expression.Literal.List(elements, type).also {
            it.context = ast.context
            it.context.firstOrNull()?.let { context.inputs.removeLast() }
        }
    }

    override fun visit(ast: RhovasAst.Expression.Literal.Object): RhovasIr {
        ast.context.firstOrNull()?.let { context.inputs.addLast(it) }
        val properties = ast.properties.mapValues { visit(it.value) }
        val type = Library.TYPES["Object"]!!
        return RhovasIr.Expression.Literal.Object(properties, type).also {
            it.context = ast.context
            it.context.firstOrNull()?.let { context.inputs.removeLast() }
        }
    }

    override fun visit(ast: RhovasAst.Expression.Literal.Type): RhovasIr.Expression.Literal.Type {
        val type = visit(ast.type).type
        val expressionType = Type.Reference(Library.TYPES["Type"]!!.base, listOf(type))
        return RhovasIr.Expression.Literal.Type(type, expressionType).also {
            it.context = ast.context
        }
    }

    override fun visit(ast: RhovasAst.Expression.Group): RhovasIr.Expression.Group {
        ast.context.firstOrNull()?.let { context.inputs.addLast(it) }
        val expression = visit(ast.expression)
        return RhovasIr.Expression.Group(expression).also {
            it.context = ast.context
            it.context.firstOrNull()?.let { context.inputs.removeLast() }
        }
    }

    override fun visit(ast: RhovasAst.Expression.Unary): RhovasIr.Expression.Unary {
        ast.context.firstOrNull()?.let { context.inputs.addLast(it) }
        val expression = visit(ast.expression)
        val method = expression.type.methods[ast.operator, listOf()] ?: throw error(
            ast,
            "Undefined method.",
            "The method op${ast.operator}() is not defined in ${expression.type.base.name}.",
        )
        return RhovasIr.Expression.Unary(ast.operator, expression, method).also {
            it.context = ast.context
            it.context.firstOrNull()?.let { context.inputs.removeLast() }
        }
    }

    override fun visit(ast: RhovasAst.Expression.Binary): RhovasIr.Expression.Binary {
        val left = visit(ast.left)
        val right = visit(ast.right)
        ast.context.firstOrNull()?.let { context.inputs.addLast(it) }
        val (type, method) = when (ast.operator) {
            "&&", "||" -> {
                require(left.type.isSubtypeOf(Library.TYPES["Boolean"]!!)) { error(
                    ast.left,
                    "Invalid binary operand.",
                    "A logical binary expression requires the left operand to be type Boolean, but received ${left.type}.",
                ) }
                require(right.type.isSubtypeOf(Library.TYPES["Boolean"]!!)) { error(
                    ast.right,
                    "Invalid binary operand.",
                    "A logical binary expression requires the left operand to be type Boolean, but received ${left.type}.",
                ) }
                Pair(Library.TYPES["Boolean"]!!, null)
            }
            "==", "!=" -> {
                //TODO: Equatable<T> interface
                val method = left.type.methods["==", listOf(left.type)] ?: throw error(
                    ast,
                    "Undefined method.",
                    "The method op==(${left.type}) is not defined in ${left.type.base.name}.",
                )
                Pair(Library.TYPES["Boolean"]!!, method)
            }
            "===", "!==" -> {
                Pair(Library.TYPES["Boolean"]!!, null)
            }
            "<", ">", "<=", ">=" -> {
                val method = left.type.methods["<=>", listOf(right.type)] ?: throw error(
                    ast,
                    "Unresolved method.",
                    "The signature op<=>(${listOf(right.type)} could not be resolved to a method in ${left.type.base.name}.",
                )
                Pair(Library.TYPES["Boolean"]!!, method)
            }
            "+", "-", "*", "/" -> {
                val method = left.type.methods[ast.operator, listOf(right.type)] ?: throw error(
                    ast,
                    "Unresolved method.",
                    "The signature op${ast.operator}(${listOf(right.type)} could not be resolved to a method in ${left.type.base.name}.",
                )
                Pair(method.returns, method)
            }
            else -> throw AssertionError()
        }
        return RhovasIr.Expression.Binary(ast.operator, left, right, method, type).also {
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
        val receiverType = if (ast.coalesce) {
            require(receiver.type.base == Library.TYPES["Nullable"]!!.base) { error(
                ast,
                "Invalid null coalesce.",
                "Null coalescing requires the receiver to be type Nullable, but received ${receiver.type}.",
            ) }
            receiver.type.methods["get", listOf()]!!.returns
        } else {
            receiver.type
        }
        val property = receiverType.properties[ast.name] ?: throw error(
            ast,
            "Undefined property.",
            "The property getter ${ast.name}() is not defined in ${receiverType.base.name}.",
        )
        val type = when {
            ast.coalesce -> Type.Reference(Library.TYPES["Nullable"]!!.base, listOf(property.type))
            else -> property.type
        }
        return RhovasIr.Expression.Access.Property(receiver, property, ast.coalesce, type).also {
            it.context = ast.context
            it.context.firstOrNull()?.let { context.inputs.removeLast() }
        }
    }

    override fun visit(ast: RhovasAst.Expression.Access.Index): RhovasIr.Expression.Access.Index {
        ast.context.firstOrNull()?.let { context.inputs.addLast(it) }
        val receiver = visit(ast.receiver)
        val arguments = ast.arguments.map { visit(it) }
        val method = receiver.type.methods["[]", arguments.map { it.type }] ?: throw error(
            ast,
            "Unresolved method.",
            "The signature [](${arguments.map { it.type }.joinToString(", ")}) could not be resolved to a method in ${receiver.type.base.name}.",
        )
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
        val function = resolveFunction("constructor", ast, type, "", ast.arguments.size) {
            ast.arguments[it] to visit(ast.arguments[it]).also { arguments.add(it) }.type
        }
        return RhovasIr.Expression.Invoke.Constructor(type as Type.Reference, function, arguments).also {
            it.context = ast.context
            it.context.firstOrNull()?.let { context.inputs.removeLast() }
        }
    }

    override fun visit(ast: RhovasAst.Expression.Invoke.Function): RhovasIr.Expression.Invoke.Function {
        ast.context.firstOrNull()?.let { context.inputs.addLast(it) }
        val qualifier = ast.qualifier?.let { visit(it).type }
        val arguments = mutableListOf<RhovasIr.Expression>()
        val function = resolveFunction("function", ast, qualifier, ast.name, ast.arguments.size) {
            Pair(ast.arguments[it], visit(ast.arguments[it]).also { arguments.add(it) }.type)
        }
        return RhovasIr.Expression.Invoke.Function(qualifier, function, arguments).also {
            it.context = ast.context
            it.context.firstOrNull()?.let { context.inputs.removeLast() }
        }
    }

    override fun visit(ast: RhovasAst.Expression.Invoke.Method): RhovasIr.Expression.Invoke.Method {
        ast.context.firstOrNull()?.let { context.inputs.addLast(it) }
        val receiver = visit(ast.receiver)
        val receiverType = computeCoalesceReceiver(receiver, ast.coalesce)
        val arguments = mutableListOf<RhovasIr.Expression>()
        val function = resolveFunction("method", ast, receiverType, ast.name, ast.arguments.size + 1) {
            when (it) {
                0 -> Pair(ast.receiver, receiverType)
                else -> Pair(ast.arguments[it - 1], visit(ast.arguments[it - 1]).also { arguments.add(it) }.type)
            }
        }
        val method = receiverType.methods[function.name, function.parameters.drop(1).map { it.type }]!!
        val type = computeCoalesceCascadeReturn(method.returns, receiver.type, ast.coalesce, ast.cascade)
        return RhovasIr.Expression.Invoke.Method(receiver, method, ast.coalesce, ast.cascade, arguments, type).also {
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
        val function = resolveFunction("function", ast, qualifier, ast.name, ast.arguments.size + 1) {
            when (it) {
                0 -> Pair(ast.receiver, receiverType)
                else -> Pair(ast.arguments[it - 1], visit(ast.arguments[it - 1]).also { arguments.add(it) }.type)
            }
        }
        val type = computeCoalesceCascadeReturn(function.returns, receiver.type, ast.coalesce, ast.cascade)
        return RhovasIr.Expression.Invoke.Pipeline(receiver, qualifier, function, ast.coalesce, ast.cascade, arguments, type).also {
            it.context = ast.context
            it.context.firstOrNull()?.let { context.inputs.removeLast() }
        }
    }

    private fun computeCoalesceReceiver(receiver: RhovasIr.Expression, coalesce: Boolean): Type {
        return if (coalesce) {
            require(receiver.type.base == Library.TYPES["Nullable"]!!.base) { error(
                "Invalid null coalesce.",
                "Null coalescing requires the receiver to be type Nullable, but received ${receiver.type}.",
                receiver.context.firstOrNull(),
            ) }
            receiver.type.methods["get", listOf()]!!.returns
        } else {
            receiver.type
        }
    }

    private fun computeCoalesceCascadeReturn(returns: Type, receiver: Type, coalesce: Boolean, cascade: Boolean): Type {
        return when {
            cascade -> receiver
            coalesce -> when {
                returns.isSubtypeOf(Library.TYPES["Nullable"]!!) -> returns
                else -> Type.Reference(Library.TYPES["Nullable"]!!.base, listOf(returns))
            }
            else -> returns
        }
    }

    private fun resolveFunction(
        term: String,
        ast: RhovasAst.Expression.Invoke,
        qualifier: Type?,
        name: String,
        arity: Int,
        generator: (Int) -> Pair<RhovasAst.Expression, Type>,
    ): Function {
        val descriptor = listOfNotNull(qualifier ?: "", name.takeIf { it.isNotEmpty() }).joinToString(".") + "/" + (if (term == "method") arity - 1 else arity)
        val candidates = (qualifier?.functions?.get(name, arity) ?: context.scope.functions[name, arity])
            .map { Pair(it, mutableMapOf<String, Type>()) }
            .ifEmpty { throw error(ast,
                "Undefined ${term}.",
                "The ${term} ${descriptor} is not defined.",
            ) }
        val filtered = candidates.toMutableList()
        val arguments = mutableListOf<Type>()
        for (i in 0 until arity) {
            //TODO: Context for inferred types
            val (ast, type) = generator(i).also { arguments.add(it.second) }
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
        val function = (qualifier?.functions?.get(name, arguments) ?: context.scope.functions[name, arguments])!!
        function.throws.forEach { exception ->
            require(context.exceptions.any { exception.isSubtypeOf(it) }) { error(ast,
                "Uncaught exception.",
                "An exception is thrown of type ${exception}, but this exception is never caught or declared.",
            ) }
        }
        return function
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
            val function = context.scope.functions[ast.name, listOf(Type.Reference(Library.TYPES["List"]!!.base, listOf(Library.TYPES["String"]!!)), Type.Reference(Library.TYPES["List"]!!.base, listOf(Library.TYPES["Dynamic"]!!)))] ?: throw error(
                ast,
                "Undefined DSL transformer.",
                "The DSL ${ast.name} requires a transformer function ${ast.name}(List<String>, List<Dynamic>).",
            )
            val literals = RhovasIr.Expression.Literal.List(
                source.literals.map { RhovasIr.Expression.Literal.String(listOf(it), listOf(), Library.TYPES["String"]!!) },
                Type.Reference(Library.TYPES["List"]!!.base, listOf(Library.TYPES["String"]!!)),
            )
            val arguments = RhovasIr.Expression.Literal.List(
                source.arguments.map { visit(it as RhovasAst.Expression) },
                Type.Reference(Library.TYPES["List"]!!.base, listOf(Library.TYPES["Dynamic"]!!)),
            )
            //TODO: Compile-time macro invocation (including argument analysis)
            return RhovasIr.Expression.Invoke.Function(null, function, listOf(literals, arguments)).also {
                it.context = ast.context
                it.context.firstOrNull()?.let { context.inputs.removeLast() }
            }
        }
    }

    override fun visit(ast: RhovasAst.Expression.Lambda): RhovasIr.Expression.Lambda {
        ast.context.firstOrNull()?.let { context.inputs.addLast(it) }
        //TODO: Validate using same requirements as function statements
        //TODO: Type inference/unification for parameter types
        //TODO: Forward thrown exceptions from context into declaration
        val parameters = ast.parameters.map { Variable.Declaration(it.first, it.second?.let { visit(it).type } ?: Library.TYPES["Dynamic"]!!, false) }
        val function = Function.Declaration("lambda", listOf(), parameters, Library.TYPES["Dynamic"]!!, listOf())
        return analyze(context.child().with(FunctionContext(function))) {
            if (parameters.isNotEmpty()) {
                parameters.forEach { (context.scope as Scope.Declaration).variables.define(it) }
            } else {
                (context.scope as Scope.Declaration).variables.define(Variable.Declaration("val", Library.TYPES["Dynamic"]!!, false))
            }
            val body = visit(ast.body)
            require(body.type.isSubtypeOf(context.function!!.returns) || context.jumps.contains("")) { error(
                ast,
                "Invalid return value type.",
                "The enclosing function ${context.function!!.name}/${context.function!!.parameters.size} requires the return value to be type ${context.function!!.returns}, but received ${body.type}.",
            ) }
            val type = Type.Reference(Library.TYPES["Lambda"]!!.base, listOf(Library.TYPES["Dynamic"]!!, Library.TYPES["Dynamic"]!!))
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
        val value = visit(ast.value)
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
            val predicate = visit(ast.predicate)
            Pair(pattern, predicate)
        }
        require(predicate.type.isSubtypeOf(Library.TYPES["Boolean"]!!)) { error(
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
        //TODO: Fix List supertype check (requires variance for generics)
        require(Type.Reference(Library.TYPES["List"]!!.base, listOf(Library.TYPES["Dynamic"]!!)).isSubtypeOf(context.pattern.type)) { error(
            ast,
            "Unmatchable pattern type",
            "This pattern is within a context that requires type ${context.pattern.type}, but received List.",
        ) }
        val type = when {
            context.pattern.type.isSubtypeOf(Type.Reference(Library.TYPES["List"]!!.base, listOf(Library.TYPES["Dynamic"]!!))) -> context.pattern.type.methods["get", listOf(Library.TYPES["Integer"]!!)]!!.returns
            else -> Library.TYPES["Dynamic"]!!
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
                            .forEach { context.pattern.bindings[it.key] = Type.Reference(Library.TYPES["List"]!!.base, listOf(it.value)) }
                        pattern
                    }
                }
                RhovasIr.Pattern.VarargDestructure(pattern, ast.operator, Type.Reference(Library.TYPES["List"]!!.base, listOf(type))).also {
                    it.context = ast.context
                }
            } else {
                analyze(context.with(PatternContext(type, context.pattern.bindings))) {
                    visit(it.value)
                }
            }
        }
        return RhovasIr.Pattern.OrderedDestructure(patterns, Type.Reference(Library.TYPES["List"]!!.base, listOf(type))).also {
            it.context = ast.context
            it.context.firstOrNull()?.let { context.inputs.removeLast() }
        }
    }

    override fun visit(ast: RhovasAst.Pattern.NamedDestructure): RhovasIr.Pattern.NamedDestructure {
        ast.context.firstOrNull()?.let { context.inputs.addLast(it) }
        require(Library.TYPES["Object"]!!.isSubtypeOf(context.pattern.type)) { error(
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
                    //TODO: Struct type validation
                    analyze(context.with(PatternContext(Library.TYPES["Dynamic"]!!, context.pattern.bindings))) {
                        val pattern = visit(it)
                        context.pattern.bindings
                            .filterKeys { !existing.containsKey(it) }
                            .forEach { context.pattern.bindings[it.key] = Library.TYPES["Object"]!! }
                        pattern
                    }
                }
                //TODO: Struct type bindings
                Pair(null, RhovasIr.Pattern.VarargDestructure(pattern, ast.operator, Library.TYPES["Object"]!!).also {
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
                //TODO: Struct type validation
                val pattern = analyze(context.with(PatternContext(Library.TYPES["Dynamic"]!!, context.pattern.bindings))) {
                    visit(it.value.second)
                }
                //TODO: Struct type bindings
                context.pattern.bindings[key] = pattern.type
                Pair(key, pattern)
            }
        }
        return RhovasIr.Pattern.NamedDestructure(patterns, Library.TYPES["Object"]!!).also {
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
            context.scope.types.define(Type.Base(ast.name, listOf(), listOf(Library.TYPES["Any"]!!), Scope.Definition(null)).reference)
            ast.context.firstOrNull()?.let { context.inputs.removeLast() }
        }

        fun visit(ast: RhovasAst.Component.Class) {
            ast.context.firstOrNull()?.let { context.inputs.addLast(it) }
            require(!context.scope.types.isDefined(ast.name, true)) { error(
                ast,
                "Redefined type.",
                "The type ${ast.name} is already defined in this scope.",
            ) }
            context.scope.types.define(Type.Base(ast.name, listOf(), listOf(Library.TYPES["Any"]!!), Scope.Definition(null)).reference)
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
            val struct = context.scope.types[ast.name]!!
            val members = ast.members.map { visit(it, struct) }.toMutableList()
            //TODO: Sequence constructor?
            //TODO: Typecheck argument (struct type / named options?)
            struct.base.scope.functions.define(Function.Definition(Function.Declaration("", listOf(), listOf(Variable.Declaration("fields", Library.TYPES["Object"]!!, false)), struct, listOf())))
            struct.base.scope.functions.define(Function.Definition(Function.Declaration("toString", listOf(), listOf(Variable.Declaration("this", struct, false)), Library.TYPES["String"]!!, listOf())))
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
            val setter = if (ast.mutable) Function.Definition(Function.Declaration(ast.name, listOf(), listOf(Variable.Declaration("this", component, false), Variable.Declaration("value", type, false)), Library.TYPES["Void"]!!, listOf())) else null
            component.base.scope.functions.define(getter)
            setter?.let { component.base.scope.functions.define(it) }
            return RhovasIr.DefinitionPhase.Member.Property(ast, getter, setter).also {
                ast.context.firstOrNull()?.let { context.inputs.removeLast() }
            }
        }

        fun visit(ast: RhovasAst.Member.Initializer, component: Type): RhovasIr.DefinitionPhase.Member.Initializer {
            ast.context.firstOrNull()?.let { context.inputs.addLast(it) }
            val parameters = ast.parameters.mapIndexed { index, parameter ->
                val type = parameter.second?.let { visit(it).type } ?: component.takeIf { index == 0 } ?: Library.TYPES["Dynamic"]!!
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
                val generics = ast.generics.map { Type.Generic(it.first, it.second?.let { visit(it).type } ?: Library.TYPES["Any"]!!) }
                generics.forEach { context.scope.types.define(it, it.name) }
                val parameters = ast.parameters.mapIndexed { index, parameter ->
                    val type = parameter.second?.let { visit(it).type } ?: component.takeIf { index == 0 } ?: Library.TYPES["Dynamic"]!!
                    Variable.Declaration(parameter.first, type, false)
                }
                val returns = ast.returns?.let { visit(it).type } ?: Library.TYPES["Void"]!! //TODO or Dynamic?
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
