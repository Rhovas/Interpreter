import com.github.jengelman.gradle.plugins.shadow.tasks.ShadowJar
import org.gradle.jvm.tasks.Jar
import org.jetbrains.kotlin.gradle.targets.js.testing.KotlinJsTest

plugins {
    kotlin("multiplatform") version "2.4.0"
    id("io.kotest") version "6.1.11"
    id("com.google.devtools.ksp") version "2.3.9"
    id("com.gradleup.shadow") version "8.3.11"
}

group = "dev.rhovas.interpreter"
version = ""

repositories {
    mavenCentral()
}

kotlin {
    js(IR) {
        binaries.executable()
        nodejs()
    }
    jvm()

    sourceSets {
        val commonMain by getting {
            dependencies {
                implementation("com.ionspin.kotlin:bignum:0.3.10")
            }
        }
        val commonTest by getting {
            dependencies {
                implementation(kotlin("test"))
                implementation("io.kotest:kotest-framework-engine:6.1.11")
            }
        }
        val jsMain by getting {}
        val jsTest by getting {}
        val jvmMain by getting {}
        val jvmTest by getting {
            dependencies {
                implementation("io.kotest:kotest-runner-junit5:6.1.11")
            }
        }
    }
}

tasks.withType<Test> {
    useJUnitPlatform()
}

val copyJsTestResources = task<Copy>("copyJsTestResource") {
    from("/src/commonTest/resources")
    into("${project.buildDir}/js/packages/${project.name}-test/src/commonTest/resources")
}

tasks.withType<KotlinJsTest> {
    dependsOn(copyJsTestResources)
}

val shadowJvmJar = task<ShadowJar>("shadowJvmJar") {
    from(tasks.getByName<Jar>("jvmJar").archiveFile)
    configurations.add(project.configurations.getByName("jvmRuntimeClasspath"))
    archiveClassifier.set("")
    manifest {
        attributes["Main-Class"] = "${project.group}.MainKt"
    }
}

tasks.build {
    dependsOn(shadowJvmJar)
}
