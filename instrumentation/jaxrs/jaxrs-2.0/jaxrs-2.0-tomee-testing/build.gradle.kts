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
