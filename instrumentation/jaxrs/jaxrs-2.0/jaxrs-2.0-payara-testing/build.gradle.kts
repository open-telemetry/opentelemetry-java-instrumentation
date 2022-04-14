plugins {
  id("otel.javaagent-testing")
}

otelJava {
  // Test fails to start on java 17
  maxJavaVersionForTests.set(JavaVersion.VERSION_11)
}

dependencies {
  testImplementation(project(":instrumentation:jaxrs:jaxrs-2.0:jaxrs-2.0-arquillian-testing"))
  testRuntimeOnly("fish.payara.arquillian:arquillian-payara-server-embedded:2.4.1")
  testRuntimeOnly("fish.payara.extras:payara-embedded-web:5.2021.2")

  testInstrumentation(project(":instrumentation:servlet:servlet-3.0:javaagent"))
  testInstrumentation(project(":instrumentation:jaxrs:jaxrs-2.0:jaxrs-2.0-annotations:javaagent"))
  testInstrumentation(project(":instrumentation:jaxrs:jaxrs-2.0:jaxrs-2.0-jersey-2.0:javaagent"))
}
