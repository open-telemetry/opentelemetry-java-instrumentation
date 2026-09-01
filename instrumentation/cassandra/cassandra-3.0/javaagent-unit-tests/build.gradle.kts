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

  // The javaagent module pins its testLibrary to driver 3.2.0, which has neither SniEndPoint nor
  // Host.getEndPoint(), so the SNI path cannot be reached from its tests. SNI arrived in driver
  // 3.8.0; 3.11.5 is the last 3.x release. Testing SNI here lets the javaagent module keep testing
  // the old 3.x drivers it exists for.
  testImplementation("com.datastax.cassandra:cassandra-driver-core:3.11.5")
}
