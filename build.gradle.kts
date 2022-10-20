import com.github.jengelman.gradle.plugins.shadow.tasks.ShadowJar
import org.gradle.jvm.tasks.Jar
import org.jetbrains.kotlin.gradle.targets.js.testing.KotlinJsTest
import org.jetbrains.kotlin.gradle.targets.jvm.tasks.KotlinJvmTest
import org.jetbrains.kotlin.gradle.tasks.KotlinCompile

plugins {
    kotlin("multiplatform") version "1.7.20"
    id("com.github.johnrengelman.shadow") version "7.1.2"
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
                implementation("com.ionspin.kotlin:bignum:0.3.7")
            }
        }
        val commonTest by getting {
            dependencies {
                implementation(kotlin("test"))
            }
        }
        val jsMain by getting {}
        val jsTest by getting {}
        val jvmMain by getting {}
        val jvmTest by getting {
            dependencies {
                implementation("org.junit.jupiter:junit-jupiter:5.7.2")
            }
        }
    }
}

tasks.withType<KotlinCompile> {
    kotlinOptions.jvmTarget = "1.8"
}

val copyJsTestResources = task<Copy>("copyJsTestResource") {
    from("/src/commonTest/resources")
    into("${project.buildDir}/js/packages/${project.name}-test/src/commonTest/resources")
}

tasks.withType<KotlinJsTest> {
    dependsOn(copyJsTestResources)
}

tasks.withType<KotlinJvmTest> {
    useJUnitPlatform()
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
