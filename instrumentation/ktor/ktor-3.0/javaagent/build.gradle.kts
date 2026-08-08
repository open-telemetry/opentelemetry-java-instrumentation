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
    // the client span context bridging in KtorClientExtensionsInstrumentation pulls in the
    // opentelemetry-api instrumentation, which needs the api on the classpath and its own module
    // excluded from this module's muzzle check
    extraDependency("io.opentelemetry:opentelemetry-api:1.0.0")
    excludeInstrumentationName("opentelemetry-api")
  }
  pass {
    group.set("io.ktor")
    module.set("ktor-server-core")
    versions.set("[3.0.0,)")
    assertInverse.set(true)
    excludeInstrumentationName("ktor-client")
    extraDependency("io.opentelemetry:opentelemetry-api:1.0.0")
    excludeInstrumentationName("opentelemetry-api")
  }
}

val ktorVersion = "3.0.0"

dependencies {
  library("io.ktor:ktor-client-core:$ktorVersion")
  library("io.ktor:ktor-server-core:$ktorVersion")

  implementation(project(":instrumentation:ktor:ktor-3.0:library"))

  // needed for bridging the shaded Context stored on the call back to the application context in
  // KtorClientExtensionsInstrumentation
  implementation(project(":instrumentation:opentelemetry-api:opentelemetry-api-1.0:javaagent"))
  // see the comment in opentelemetry-api-1.0.gradle for more details
  compileOnly(project(":opentelemetry-api-shaded-for-instrumenting", configuration = "shadow"))

  compileOnly("org.jetbrains.kotlin:kotlin-stdlib-jdk8")

  testInstrumentation(project(":instrumentation:netty:netty-4.1:javaagent"))
  testInstrumentation(project(":instrumentation:ktor:ktor-2.0:javaagent"))
  testInstrumentation(project(":instrumentation:kotlinx-coroutines:kotlinx-coroutines-1.0:javaagent"))
  testInstrumentation(project(":instrumentation:opentelemetry-extension-kotlin-1.0:javaagent"))

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

tasks {
  val testExperimental = register<Test>("testExperimental") {
    testClassesDirs = sourceSets.test.get().output.classesDirs
    classpath = sourceSets.test.get().runtimeClasspath

    jvmArgs("-Dotel.instrumentation.http.server.emit-experimental-telemetry=true")
  }

  val testStableSemconv = register<Test>("testStableSemconv") {
    testClassesDirs = sourceSets.test.get().output.classesDirs
    classpath = sourceSets.test.get().runtimeClasspath
    jvmArgs("-Dotel.semconv-stability.opt-in=service.peer")
  }

  check {
    dependsOn(testExperimental, testStableSemconv)
  }
}
