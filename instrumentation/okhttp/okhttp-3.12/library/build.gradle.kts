plugins {
  id("otel.library-instrumentation")
  id("otel.instrumentation-version-class")
  id("otel.nullaway-conventions")
  id("otel.animalsniffer-conventions")
}

instrumentationVersionClass {
  className.set("io.opentelemetry.instrumentation.okhttp.v3_12.internal.InstrumentationVersion")
}

dependencies {
  // okhttp 3.11.0 introduced the granular EventListener network-phase callbacks that this
  // instrumentation relies on; 3.12.0 adds Call.timeout(), which lets the call wrapper avoid a
  // reflection-based shim.
  library("com.squareup.okhttp3:okhttp:3.12.0")

  testImplementation(project(":instrumentation:okhttp:okhttp-3.0:testing"))
}

tasks {
  val testStableSemconv by registering(Test::class) {
    testClassesDirs = sourceSets.test.get().output.classesDirs
    classpath = sourceSets.test.get().runtimeClasspath
    jvmArgs("-Dotel.semconv-stability.opt-in=service.peer")
  }

  check {
    dependsOn(testStableSemconv)
  }
}
