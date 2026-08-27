plugins {
  id("otel.java-conventions")
}

tasks {
  val testStableSemconv = register<Test>("testStableSemconv") {
    testClassesDirs = sourceSets.test.get().output.classesDirs
    classpath = sourceSets.test.get().runtimeClasspath

    jvmArgs("-Dotel.semconv-stability.opt-in=database")
  }

  check {
    dependsOn(testStableSemconv)
  }
}

dependencies {
  testImplementation(project(":instrumentation:cassandra:cassandra-4.0:javaagent"))
  testImplementation(project(":instrumentation-api-incubator"))

  // The javaagent module compiles against driver 4.0.0, where SniEndPoint does not exist. SNI
  // arrived in driver 4.3, so this module uses 4.3.1 to test the reflective detection.
  testImplementation("com.datastax.oss:java-driver-core:4.3.1")
}
