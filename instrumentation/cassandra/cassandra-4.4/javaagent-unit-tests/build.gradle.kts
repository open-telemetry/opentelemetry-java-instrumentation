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
  testImplementation(project(":instrumentation:cassandra:cassandra-4.4:javaagent"))
  testImplementation(project(":instrumentation:cassandra:cassandra-4.4:library"))
  testImplementation(project(":instrumentation-api-incubator"))
  testImplementation(project(":javaagent-extension-api"))

  testImplementation("com.datastax.oss:java-driver-core:4.4.0")
}
