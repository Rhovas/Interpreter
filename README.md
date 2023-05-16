# Rhovas

Welcome! Rhovas is a programming language intended for API design and
enforcement. Using Rhovas, developers can better express the contracts and
intention of their code to help create maintainable, correct, and performant
software.

This repository is the implementation of Rhovas itself. For more information on
Rhovas, see the following links:

 - [Rhovas Website](https://rhovas.dev): Information about Rhovas, most notably:
   - [Language Tour](https://rhovas.dev/learn/tour): A high-level overview of
     Rhovas that covers most of the important constructs/features.
   - [Online Editor](https://rhovas.dev/editor): An online editor for Rhovas
     which can be used without having to setup/build this project locally.
 - [Discord Server](https://discord.gg/gm96xd8): Discussion on Rhovas including
   language design, implementation, and anything related.

## Project Structure & Setup

This information is for other developers to understand and contribute to the
Rhovas interpreter. To try out the language itself, see the [Online Editor](https://rhovas.dev/editor).

Rhovas is setup as a Kotlin Multiplatform project using Gradle. Kotlin is very
similar to Rhovas, and multiplatform support allows both the online editor
(`js`) and the command-line executable (`jvm`). IntelliJ is recommended for it's
Gradle support (plus Kotlin); for details on how to open Gradle projects and run
tasks see the [IntelliJ Gradle docs](https://www.jetbrains.com/help/idea/gradle.html).

The project is structured as follows:

 - `commonMain`/`commonTest`: The core implementation of Rhovas shared between
   platforms. This is further subdivided into other packages but should be
   relatively easy to navigate.
    - `commonTest` contains a mix of unit and integration (full-program) tests.
 - `jsMain`/`jsTest`: Defines JavaScript bindings for use in the online editor.
 - `jvmMain`/`jvmTest`: Defines the Java/JVM main function for evaluating files
   and running the REPL.

A few notable Gradle tasks are documented below. Gradle (and IntelliJ) will
likely take some time to configure and index, as well as on the first build
(which has to compile everything and setup nodejs for `jsTest`).

 - `build/build`: Builds all executables and runs all tests (`js` + `jvm`).
   - `js` executables are in `build/compileSync/main/productionExecutable`
   - `jvm` executable is in `build/libs/Interpreter-x.y.z.jar`
 - `build/clean`: Cleans the full `build` directory.
 - `other/compileDevelopmentExecutableKotlinJs`: Builds a development executable
   for Kotlin/JS, which minimizes most of the identifier mangling for debugging.
   - executable is in `build/compileSync/main/developmentExecutable`
 - `verification/allTests`: Runs all tests (`js` + `jvm`)

## Sponsor Me!

If you appreciate the work I do, please consider sponsoring me! Sponsors receive
a `Sponsor` role on the [Discord Server](https://discord.gg/gm96xd8) and will
also be listed on the Rhovas website.

 - [GitHub Sponsors](https://github.com/sponsors/WillBAnders)
