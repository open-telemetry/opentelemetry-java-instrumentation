plugins {
  id("otel.javaagent-instrumentation")
}

muzzle {
  pass {
    group.set("jakarta.ws.rs")
    module.set("jakarta.ws.rs-api")
    versions.set("[3.0.0,)")
    assertInverse.set(true)
  }
}

dependencies {
  bootstrap(project(":instrumentation:jaxrs:jaxrs-common:bootstrap"))

  implementation(project(":instrumentation:jaxrs:jaxrs-3.0:jaxrs-3.0-common:javaagent"))

  testInstrumentation(project(":instrumentation:jaxrs:jaxrs-2.0:jaxrs-2.0-annotations:javaagent"))

  compileOnly("jakarta.ws.rs:jakarta.ws.rs-api:3.0.0")

  testImplementation("jakarta.ws.rs:jakarta.ws.rs-api:3.0.0")
}

tasks {
  val testControllerTelemetry = register<Test>("testControllerTelemetry") {
    testClassesDirs = sourceSets.test.get().output.classesDirs
    classpath = sourceSets.test.get().runtimeClasspath

    jvmArgs("-Dotel.instrumentation.common.controller-telemetry.enabled=true")
    systemProperty("metadataConfig", "otel.instrumentation.common.controller-telemetry.enabled=true")
  }

  check {
    dependsOn(testControllerTelemetry)
  }
}
