package dev.rhovas.interpreter

import java.io.File

actual object Platform {

    actual val TARGET: Target = Target.JVM

    actual fun readFile(path: String): String {
        return File(path).readText()
    }

}
