import com.github.jengelman.gradle.plugins.shadow.tasks.ShadowJar
import org.gradle.jvm.tasks.Jar
import org.jetbrains.kotlin.gradle.targets.js.testing.KotlinJsTest

plugins {
    kotlin("multiplatform") version "2.0.0"
    id("io.kotest.multiplatform") version "5.9.1"
    id("com.github.johnrengelman.shadow") version "8.1.1"
}

group = "dev.rhovas.interpreter"
version = "0.0.0"

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
                implementation("com.ionspin.kotlin:bignum:0.3.8")
            }
        }
        val commonTest by getting {
            dependencies {
                implementation(kotlin("test"))
                implementation("io.kotest:kotest-framework-engine:5.6.2")
            }
        }
        val jsMain by getting {}
        val jsTest by getting {}
        val jvmMain by getting {}
        val jvmTest by getting {
            dependencies {
                implementation("io.kotest:kotest-runner-junit5:5.6.2")
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
