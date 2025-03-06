plugins {
  id("otel.javaagent-testing")
}

dependencies {
  testImplementation(project(":instrumentation:jaxrs:jaxrs-2.0:jaxrs-2.0-arquillian-testing"))
  testCompileOnly("jakarta.enterprise:jakarta.enterprise.cdi-api:2.0.2")
  testRuntimeOnly("org.apache.tomee:arquillian-tomee-embedded:8.0.6")
  testRuntimeOnly("org.apache.tomee:tomee-embedded:8.0.6")
  testRuntimeOnly("org.apache.tomee:tomee-jaxrs:8.0.6")

  testInstrumentation(project(":instrumentation:servlet:servlet-3.0:javaagent"))
  testInstrumentation(project(":instrumentation:jaxrs:jaxrs-2.0:jaxrs-2.0-annotations:javaagent"))
  testInstrumentation(project(":instrumentation:jaxrs:jaxrs-2.0:jaxrs-2.0-cxf-3.2:javaagent"))
}

otelJava {
  // due to security manager deprecation this test does not work on jdk 24 with default configuration
  maxJavaVersionForTests.set(JavaVersion.VERSION_23)
}

tasks.withType<Test>().configureEach {
  // required on jdk17
  jvmArgs("--add-opens=java.base/java.lang=ALL-UNNAMED")
  jvmArgs("--add-exports=java.base/sun.misc=ALL-UNNAMED")
  jvmArgs("-XX:+IgnoreUnrecognizedVMOptions")
  jvmArgs("-Dotel.instrumentation.common.experimental.controller-telemetry.enabled=true")
}
