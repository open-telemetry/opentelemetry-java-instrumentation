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
  testImplementation(project(":instrumentation:cassandra:cassandra-3.0:javaagent"))
  testImplementation(project(":instrumentation-api-incubator"))

  // SniEndPoint and Host.getEndPoint() were added in driver 3.8.0. Use the final 3.x release
  // to test these APIs while the javaagent integration tests remain pinned to 3.2.0.
  testImplementation("com.datastax.cassandra:cassandra-driver-core:3.11.5")
}
