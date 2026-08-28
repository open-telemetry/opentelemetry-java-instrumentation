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
  testImplementation(project(":instrumentation-api-incubator"))
  testImplementation(
    project(
      ":instrumentation:elasticsearch:elasticsearch-transport-common-5.0:javaagent",
    ),
  )

  testImplementation("org.elasticsearch.client:transport:5.0.0")
  testImplementation("org.apache.logging.log4j:log4j-api:2.11.0")
  testImplementation("org.apache.logging.log4j:log4j-core:2.11.0")
}
