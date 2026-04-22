plugins {
  id("otel.javaagent-instrumentation")
}

muzzle {
  pass {
    group.set("io.ratpack")
    module.set("ratpack-core")
    versions.set("[1.7.0,)")
    assertInverse.set(true)
  }
}

dependencies {
  library("io.ratpack:ratpack-core:1.7.0")

  implementation(project(":instrumentation:netty:netty-4.1:library"))
  implementation(project(":instrumentation:ratpack:ratpack-1.7:library"))

  testImplementation(project(":instrumentation:ratpack:ratpack-1.4:testing"))
  testInstrumentation(project(":instrumentation:ratpack:ratpack-1.4:javaagent"))
}

tasks {
  withType<Test>().configureEach {
    systemProperty("testLatestDeps", otelProps.testLatestDeps)
    jvmArgs("-Dotel.instrumentation.common.experimental.controller-telemetry.enabled=true")
    systemProperty("collectMetadata", otelProps.collectMetadata)
  }

  val testStableSemconv by registering(Test::class) {
    testClassesDirs = sourceSets.test.get().output.classesDirs
    classpath = sourceSets.test.get().runtimeClasspath
    jvmArgs("-Dotel.semconv-stability.opt-in=service.peer")
    systemProperty("metadataConfig", "otel.semconv-stability.opt-in=service.peer")
  }

  check {
    dependsOn(testStableSemconv)
  }

  if (otelProps.denyUnsafe) {
    withType<Test>().configureEach {
      enabled = false
    }
  }
}
