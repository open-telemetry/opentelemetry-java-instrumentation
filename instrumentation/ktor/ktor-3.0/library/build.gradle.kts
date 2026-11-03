import io.opentelemetry.instrumentation.gradle.executions
import org.gradle.api.plugins.jvm.JvmTestSuite
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

  api(project(":instrumentation:ktor:ktor-common-2.0:library"))
  implementation("io.opentelemetry:opentelemetry-extension-kotlin")

  compileOnly("org.jetbrains.kotlin:kotlin-stdlib-jdk8")

  testImplementation(project(":instrumentation:ktor:ktor-3.0:testing"))
  testImplementation("org.jetbrains.kotlin:kotlin-stdlib-jdk8")

  testLibrary("io.ktor:ktor-server-netty:$ktorVersion")
  testLibrary("io.ktor:ktor-client-cio:$ktorVersion")
}

kotlin {
  compilerOptions {
    jvmTarget.set(JvmTarget.JVM_1_8)
    languageVersion.set(KotlinVersion.KOTLIN_2_0)
  }
}

testing.suites.named<JvmTestSuite>("test") {
  executions {
    register("Experimental") {
      systemProperty("test.emitExperimentalHttpServerTelemetry", "true")
      systemProperty("metadataConfig", "Experimental.emitExperimentalTelemetry(this)")
    }
  }
}
