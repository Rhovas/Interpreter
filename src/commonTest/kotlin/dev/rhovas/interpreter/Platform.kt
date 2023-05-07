package dev.rhovas.interpreter

enum class Target { JS, JVM }

expect object Platform {

    val TARGET: Target

    fun readFile(path: String): String

}
