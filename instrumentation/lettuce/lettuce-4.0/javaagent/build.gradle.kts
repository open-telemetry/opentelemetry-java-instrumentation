plugins {
  id("otel.javaagent-instrumentation")
}

muzzle {
  pass {
    group.set("biz.paluch.redis")
    module.set("lettuce")
    versions.set("[4.0.Final,)")
    assertInverse.set(true)
  }
}

dependencies {
  library("biz.paluch.redis:lettuce:4.0.Final")

  testInstrumentation(project(":instrumentation:lettuce:lettuce-5.0:javaagent"))
  testInstrumentation(project(":instrumentation:lettuce:lettuce-5.1:javaagent"))

  latestDepTestLibrary("biz.paluch.redis:lettuce:4.+") // see lettuce-5.0 module
}

tasks {
  withType<Test>().configureEach {
    usesService(gradle.sharedServices.registrations["testcontainersBuildService"].service)
    systemProperty("collectMetadata", otelProps.collectMetadata)
  }

  val testExperimental = register<Test>("testExperimental") {
    testClassesDirs = sourceSets.test.get().output.classesDirs
    classpath = sourceSets.test.get().runtimeClasspath

    jvmArgs("-Dotel.instrumentation.lettuce.experimental-span-attributes=true")
    systemProperty("metadataConfig", "otel.instrumentation.lettuce.experimental-span-attributes=true")
  }

  val testConnectionTelemetryEnabled = register<Test>("testConnectionTelemetryEnabled") {
    testClassesDirs = sourceSets.test.get().output.classesDirs
    classpath = sourceSets.test.get().runtimeClasspath

    jvmArgs("-Dotel.instrumentation.lettuce.connection-telemetry.enabled=true")
    systemProperty("metadataConfig", "otel.instrumentation.lettuce.connection-telemetry.enabled=true")
  }

  val testStableSemconv = register<Test>("testStableSemconv") {
    testClassesDirs = sourceSets.test.get().output.classesDirs
    classpath = sourceSets.test.get().runtimeClasspath
    jvmArgs("-Dotel.semconv-stability.opt-in=database,service.peer")
    systemProperty("metadataConfig", "otel.semconv-stability.opt-in=database,service.peer")
  }

  val testConnectionTelemetryEnabledStableSemconv =
    register<Test>("testConnectionTelemetryEnabledStableSemconv") {
      testClassesDirs = sourceSets.test.get().output.classesDirs
      classpath = sourceSets.test.get().runtimeClasspath
      jvmArgs(
        "-Dotel.instrumentation.lettuce.connection-telemetry.enabled=true",
        "-Dotel.semconv-stability.opt-in=database,service.peer"
      )
      systemProperty(
        "metadataConfig",
        "otel.instrumentation.lettuce.connection-telemetry.enabled=true,otel.semconv-stability.opt-in=database,service.peer"
      )
    }

  check {
    dependsOn(
      testConnectionTelemetryEnabled,
      testConnectionTelemetryEnabledStableSemconv,
      testStableSemconv,
      testExperimental
    )
  }
}
