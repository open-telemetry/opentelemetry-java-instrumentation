plugins {
  id("otel.library-instrumentation")
}

val latestDepTest = findProperty("testLatestDeps") as Boolean
dependencies {
  if (latestDepTest) {
    library("org.apache.cassandra:java-driver-core:4.18.0")
  } else {
    library("com.datastax.oss:java-driver-core:4.4.0")
  }

  compileOnly("com.google.auto.value:auto-value-annotations")
  annotationProcessor("com.google.auto.value:auto-value")

  testImplementation(project(":instrumentation:cassandra:cassandra-4.4:testing"))
}

tasks {
  withType<Test>().configureEach {
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
