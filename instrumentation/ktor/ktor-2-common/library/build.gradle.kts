import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import org.jetbrains.kotlin.gradle.dsl.KotlinVersion

plugins {
  id("otel.library-instrumentation")
  id("org.jetbrains.kotlin.jvm")
}
dependencies {
  implementation(project(":instrumentation:ktor:ktor-common:library"))
  implementation("io.opentelemetry:opentelemetry-extension-kotlin")
  compileOnly("org.jetbrains.kotlin:kotlin-stdlib-jdk8")
  compileOnly("io.ktor:ktor-client-core:2.0.0")
  compileOnly("io.ktor:ktor-server-core:2.0.0")
}

kotlin {
  compilerOptions {
    jvmTarget.set(JvmTarget.JVM_1_8)
    @Suppress("deprecation")
    languageVersion.set(KotlinVersion.KOTLIN_1_8)
  }
}
