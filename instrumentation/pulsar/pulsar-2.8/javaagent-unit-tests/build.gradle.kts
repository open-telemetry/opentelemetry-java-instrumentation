plugins {
  id("otel.java-conventions")
}

dependencies {
  testImplementation(project(":instrumentation:pulsar:pulsar-2.8:javaagent"))
  testImplementation(project(":instrumentation-api"))
  testImplementation("org.apache.pulsar:pulsar-client:2.8.0")
}

tasks {
  val testMessagingPreview = register<Test>("testMessagingPreview") {
    testClassesDirs = sourceSets.test.get().output.classesDirs
    classpath = sourceSets.test.get().runtimeClasspath
    jvmArgs("-Dotel.semconv-stability.preview=messaging")
  }

  check {
    dependsOn(testMessagingPreview)
  }
}
