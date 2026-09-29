plugins {
  id("otel.java-conventions")
}

dependencies {
  testImplementation(project(":testing-common"))
  testImplementation(project(":instrumentation-api-incubator"))
  testImplementation(project(":instrumentation:kafka:kafka-clients:kafka-clients-common-0.11:library"))
  testImplementation(project(":instrumentation:kafka:kafka-clients:kafka-clients-0.11:bootstrap"))
  testImplementation(project(":instrumentation:kafka:kafka-clients:kafka-clients-0.11:javaagent"))
  testImplementation(project(":instrumentation:kafka:kafka-streams-0.11:javaagent"))
  testImplementation(project(":javaagent-bootstrap"))
  testImplementation(project(":javaagent-extension-api"))
  testImplementation("io.opentelemetry:opentelemetry-api")
  testImplementation("org.apache.kafka:kafka-clients:0.11.0.0")
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
