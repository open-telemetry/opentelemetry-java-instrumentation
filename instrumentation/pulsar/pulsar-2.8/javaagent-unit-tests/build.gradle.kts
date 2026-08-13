plugins {
  id("otel.java-conventions")
}

dependencies {
  testImplementation(project(":instrumentation:pulsar:pulsar-2.8:javaagent"))
  testImplementation(project(":instrumentation-api"))
  testImplementation(project(":instrumentation-api-incubator"))
  testImplementation("io.opentelemetry:opentelemetry-sdk-testing")
  testImplementation("org.apache.pulsar:pulsar-client:2.8.0")
}

tasks {
  val testMessagingPreview = register<Test>("testMessagingPreview") {
    testClassesDirs = sourceSets.test.get().output.classesDirs
    classpath = sourceSets.test.get().runtimeClasspath
    jvmArgs("-Dotel.semconv-stability.preview=messaging")
  }

  val testBothSemconv = register<Test>("testBothSemconv") {
    testClassesDirs = sourceSets.test.get().output.classesDirs
    classpath = sourceSets.test.get().runtimeClasspath
    jvmArgs("-Dotel.semconv-stability.preview=messaging/dup")
  }

  check {
    dependsOn(testMessagingPreview, testBothSemconv)
  }
}
