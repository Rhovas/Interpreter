package dev.rhovas.interpreter

actual fun readFile(path: String): String {
    val fs = js("require('fs')")
    return fs.readFileSync(path, "utf8") as String
}
