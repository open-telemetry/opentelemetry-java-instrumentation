plugins {
  id("net.bytebuddy.byte-buddy")

  id("io.opentelemetry.instrumentation.javaagent-testing")

  id("otel.java-conventions")
}

evaluationDependsOn(":testing:agent-for-testing")

dependencies {
  annotationProcessor("com.google.auto.service:auto-service")
  compileOnly("com.google.auto.service:auto-service")
  compileOnly("org.slf4j:slf4j-api")

  testImplementation("org.testcontainers:testcontainers")
}

configurations.configureEach {
  if (name.toLowerCase().endsWith("testruntimeclasspath")) {
    // Added by agent, don't let Gradle bring it in when running tests.
    exclude(module = "javaagent-bootstrap")
  }
}
