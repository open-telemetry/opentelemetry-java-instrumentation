plugins {
  id("otel.javaagent-instrumentation")
}

muzzle {
  pass {
    group.set("io.lettuce")
    module.set("lettuce-core")
    versions.set("[5.0.0.RELEASE,5.1.0.RELEASE)")
    assertInverse.set(true)
  }
}

dependencies {
  library("io.lettuce:lettuce-core:5.0.0.RELEASE")

  implementation(project(":instrumentation:lettuce:lettuce-common:library"))

  testImplementation("com.google.guava:guava")
  testImplementation("io.lettuce:lettuce-core:5.0.0.RELEASE")

  testInstrumentation(project(":instrumentation:reactor:reactor-3.1:javaagent"))
  testInstrumentation(project(":instrumentation:lettuce:lettuce-4.0:javaagent"))
  testInstrumentation(project(":instrumentation:lettuce:lettuce-5.1:javaagent"))

  latestDepTestLibrary("io.lettuce:lettuce-core:5.0.+") // see lettuce-5.1 module
}

tasks {
  withType<Test>().configureEach {
    // TODO run tests both with and without experimental span attributes and span events
    jvmArgs("-Dotel.instrumentation.lettuce.experimental-span-attributes=true")
    jvmArgs("-Dotel.instrumentation.lettuce.connection-telemetry.enabled=true")
    jvmArgs("-Dotel.instrumentation.lettuce.experimental.command-encoding-events.enabled=true")
    usesService(gradle.sharedServices.registrations["testcontainersBuildService"].service)
  }

  val testStableSemconv by registering(Test::class) {
    testClassesDirs = sourceSets.test.get().output.classesDirs
    classpath = sourceSets.test.get().runtimeClasspath
    jvmArgs("-Dotel.semconv-stability.opt-in=database")
  }

  check {
    dependsOn(testStableSemconv)
  }
}
