package dev.rhovas.interpreter

import io.kotest.core.spec.style.ShouldSpec
import io.kotest.core.spec.style.scopes.ShouldSpecContainerScope
import io.kotest.core.spec.style.scopes.ShouldSpecRootScope
import io.kotest.core.test.TestScope

/**
 * Custom Kotest Spec implementation that allows suites to work with Kotlin/JS
 * by transforming them into prefixed top-level tests. Miscellaneous notes:
 *
 *  - We extend [ShouldSpec] as Kotest searches specific classes to run tests.
 *  - We use [spec] and [suite] to avoid overlapping with other test functions.
 *  - We disable `suspend` within suites as Kotlin/JS cannot support nested
 *    promises (root cause of the original limitation). This makes registering
 *    all tests synchronous, which is not an issue for our current usage.
 *  - We delay test registration as [ContainerScope.registerJvm] must be a
 *    `suspend` function to register nested tests/contexts.
 */
// open, not abstract: https://youtrack.jetbrains.com/issue/KT-63399
open class RhovasSpec : ShouldSpec() {

    fun spec(name: String, test: suspend TestScope.() -> Unit) = should(name, test)

    fun suite(name: String, test: ContainerScope.() -> Unit) = when(Platform.TARGET) {
        Target.JS -> ContainerScope().also(test).registerJs(this, name)
        Target.JVM -> context(name) { ContainerScope().also(test).registerJvm(this) }
    }

    fun <T> suite(name: String, tests: List<Pair<String, T>>, test: suspend (T) -> Unit) = suite(name) {
        tests.forEach { (name, test) -> spec(name) { test(test) } }
    }

    class ContainerScope {

        private val specs: MutableMap<String, suspend TestScope.() -> Unit> = mutableMapOf()
        private val suites: MutableMap<String, ContainerScope> = mutableMapOf()

        fun spec(name: String, test: suspend TestScope.() -> Unit) = specs.put(name, test)
        fun suite(name: String, test: ContainerScope.() -> Unit) = suites.put(name, ContainerScope().also(test))

        fun <T> suite(name: String, tests: List<Pair<String, T>>, test: suspend (T) -> Unit) = suite(name) {
            tests.forEach { (name, test) -> spec(name) { test(test) } }
        }

        fun registerJs(scope: ShouldSpecRootScope, prefix: String) {
            fun format(prefix: String, name: String): String {
                val skip = "!".takeIf { name.startsWith("!") && !prefix.startsWith("!") } ?: ""
                val focus = "f:".takeIf { name.startsWith("f:") && !prefix.startsWith("f:") } ?: ""
                return "${skip}${focus}${prefix} | ${name.removePrefix("!").removePrefix("f:")}"
            }
            specs.forEach { scope.should(format(prefix, it.key), it.value) }
            suites.forEach { it.value.registerJs(scope, format(prefix, it.key)) }
        }

        suspend fun registerJvm(scope: ShouldSpecContainerScope) {
            specs.forEach { scope.should(it.key, it.value) }
            suites.forEach { scope.context(it.key) { it.value.registerJvm(this) } }
        }

    }

}
