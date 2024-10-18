import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import org.jetbrains.kotlin.gradle.dsl.KotlinVersion

plugins {
  id("otel.library-instrumentation")

  id("org.jetbrains.kotlin.jvm")
}

val ktorVersion = "3.0.0"

dependencies {
  library("io.ktor:ktor-client-core:$ktorVersion")
  library("io.ktor:ktor-server-core:$ktorVersion")

  implementation(project(":instrumentation:ktor:ktor-common:library"))
  implementation("io.opentelemetry:opentelemetry-extension-kotlin")

  compileOnly("org.jetbrains.kotlin:kotlin-stdlib-jdk8")

  testImplementation(project(":instrumentation:ktor:ktor-3.0:testing"))
  testImplementation("org.jetbrains.kotlin:kotlin-stdlib-jdk8")

  testLibrary("io.ktor:ktor-server-netty:$ktorVersion")
  testLibrary("io.ktor:ktor-client-cio:$ktorVersion")

  latestDepTestLibrary("io.ktor:ktor-client-core:3.+")
  latestDepTestLibrary("io.ktor:ktor-server-core:3.+")
  latestDepTestLibrary("io.ktor:ktor-server-netty:3.+")
  latestDepTestLibrary("io.ktor:ktor-client-cio:3.+")
}

kotlin {
  compilerOptions {
    jvmTarget.set(JvmTarget.JVM_1_8)
    @Suppress("deprecation")
    languageVersion.set(KotlinVersion.KOTLIN_1_6)
  }
}
