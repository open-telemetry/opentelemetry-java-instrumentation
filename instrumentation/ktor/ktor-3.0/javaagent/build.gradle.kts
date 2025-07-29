import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
  id("org.jetbrains.kotlin.jvm")
  id("otel.javaagent-instrumentation")
}

muzzle {
  pass {
    group.set("io.ktor")
    module.set("ktor-client-core")
    versions.set("[3.0.0,)")
    assertInverse.set(true)
    excludeInstrumentationName("ktor-server")
    // missing dependencies
    skip("1.1.0", "1.1.1", "1.1.5")
  }
  pass {
    group.set("io.ktor")
    module.set("ktor-server-core")
    versions.set("[3.0.0,)")
    assertInverse.set(true)
    excludeInstrumentationName("ktor-client")
    // missing dependencies
    skip("1.1.0", "1.1.1")
  }
}

val ktorVersion = "3.0.0"

dependencies {
  library("io.ktor:ktor-client-core:$ktorVersion")
  library("io.ktor:ktor-server-core:$ktorVersion")

  implementation(project(":instrumentation:ktor:ktor-3.0:library"))

  compileOnly("org.jetbrains.kotlin:kotlin-stdlib-jdk8")

  testInstrumentation(project(":instrumentation:netty:netty-4.1:javaagent"))
  testInstrumentation(project(":instrumentation:ktor:ktor-2.0:javaagent"))

  testImplementation(project(":instrumentation:ktor:ktor-3.0:testing"))
  testImplementation("org.jetbrains.kotlin:kotlin-stdlib-jdk8")
  testImplementation("io.opentelemetry:opentelemetry-extension-kotlin")

  testLibrary("io.ktor:ktor-server-netty:$ktorVersion")
  testLibrary("io.ktor:ktor-client-cio:$ktorVersion")
}

kotlin {
  compilerOptions {
    jvmTarget.set(JvmTarget.JVM_1_8)
    // generate metadata for Java 1.8 reflection on method parameters, used in @WithSpan tests
    javaParameters = true
  }
}
