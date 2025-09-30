plugins {
  id("otel.java-conventions")
}

dependencies {
  api(project(":testing-common"))
  api("io.opentelemetry:opentelemetry-sdk-testing")

  api("org.apache.logging.log4j:log4j-core:2.17.0")
  api("org.apache.logging.log4j:log4j-api:2.17.0")

  compileOnly("com.google.auto.value:auto-value-annotations")
  annotationProcessor("com.google.auto.value:auto-value")
}
