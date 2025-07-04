import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import org.jetbrains.kotlin.gradle.dsl.KotlinVersion

plugins {
  id("otel.library-instrumentation")

  id("org.jetbrains.kotlin.jvm")
}

val ktorVersion = "2.0.0"

dependencies {
  library("io.ktor:ktor-client-core:$ktorVersion")
  library("io.ktor:ktor-server-core:$ktorVersion")

  api(project(":instrumentation:ktor:ktor-2-common:library"))
  implementation("io.opentelemetry:opentelemetry-extension-kotlin")

  compileOnly("org.jetbrains.kotlin:kotlin-stdlib-jdk8")

  testImplementation(project(":instrumentation:ktor:ktor-2.0:testing"))
  testImplementation("org.jetbrains.kotlin:kotlin-stdlib-jdk8")

  testLibrary("io.ktor:ktor-server-netty:$ktorVersion")
  testLibrary("io.ktor:ktor-client-cio:$ktorVersion")

  latestDepTestLibrary("io.ktor:ktor-client-core:2.+") // see ktor-3.0 module
  latestDepTestLibrary("io.ktor:ktor-server-core:2.+") // see ktor-3.0 module
  latestDepTestLibrary("io.ktor:ktor-server-netty:2.+") // see ktor-3.0 module
  latestDepTestLibrary("io.ktor:ktor-client-cio:2.+") // see ktor-3.0 module
}

kotlin {
  compilerOptions {
    jvmTarget.set(JvmTarget.JVM_1_8)
    @Suppress("deprecation")
    languageVersion.set(KotlinVersion.KOTLIN_1_8)
  }
}
