import org.jetbrains.kotlin.gradle.dsl.JvmTarget

// Keep this Kotlin helper separate because the muzzle generation plugin does not configure Kotlin
// compile task outputs correctly.
plugins {
  id("org.jetbrains.kotlin.jvm")
  id("otel.java-conventions")
}

dependencies {
  compileOnly("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.3.0")
  compileOnly("org.jetbrains.kotlin:kotlin-stdlib-jdk8")
  compileOnly(project(":instrumentation-api"))
}

kotlin {
  compilerOptions {
    jvmTarget.set(JvmTarget.JVM_1_8)
  }
}
