package dev.rhovas.interpreter

actual object Platform {

    actual val TARGET: Target = Target.JS

    actual fun readFile(path: String): String {
        val fs = js("require('fs')")
        return fs.readFileSync(path, "utf8") as String
    }

}
