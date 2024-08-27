plugins {
  id("otel.javaagent-testing")
}

dependencies {
  testImplementation(project(":instrumentation:jaxws:jaxws-2.0-arquillian-testing"))
  testCompileOnly("jakarta.enterprise:jakarta.enterprise.cdi-api:2.0.2")
  testRuntimeOnly("org.apache.tomee:arquillian-tomee-embedded:8.0.6")
  testRuntimeOnly("org.apache.tomee:tomee-embedded:8.0.6")
  testRuntimeOnly("org.apache.tomee:tomee-webservices:8.0.6")

  testInstrumentation(project(":instrumentation:servlet:servlet-3.0:javaagent"))
  testInstrumentation(project(":instrumentation:jaxws:jaxws-2.0:javaagent"))
  testInstrumentation(project(":instrumentation:jaxws:jaxws-cxf-3.0:javaagent"))
  testInstrumentation(project(":instrumentation:jaxws:jaxws-jws-api-1.1:javaagent"))
}

tasks.withType<Test>().configureEach {
  // required on jdk17
  jvmArgs("--add-opens=java.base/java.lang=ALL-UNNAMED")
  jvmArgs("--add-exports=java.base/sun.misc=ALL-UNNAMED")
  jvmArgs("-XX:+IgnoreUnrecognizedVMOptions")
  jvmArgs("-Dotel.instrumentation.common.experimental.controller-telemetry.enabled=true")
}
