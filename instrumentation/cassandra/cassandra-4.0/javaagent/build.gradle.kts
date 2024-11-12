plugins {
  id("otel.javaagent-instrumentation")
}

muzzle {
  pass {
    group.set("com.datastax.oss")
    module.set("java-driver-core")
    versions.set("[4.0,4.4)")
    assertInverse.set(true)
  }
}

dependencies {
  library("com.datastax.oss:java-driver-core:4.0.0")

  compileOnly("com.google.auto.value:auto-value-annotations")
  annotationProcessor("com.google.auto.value:auto-value")

  testImplementation(project(":instrumentation:cassandra:cassandra-4-common:testing"))

  testInstrumentation(project(":instrumentation:cassandra:cassandra-3.0:javaagent"))
  testInstrumentation(project(":instrumentation:cassandra:cassandra-4.4:javaagent"))
}

tasks {
  withType<Test>().configureEach {
    usesService(gradle.sharedServices.registrations["testcontainersBuildService"].service)
  }

  val testStableSemconv by registering(Test::class) {
    jvmArgs("-Dotel.semconv-stability.opt-in=database")
  }

  check {
    dependsOn(testStableSemconv)
  }
}
