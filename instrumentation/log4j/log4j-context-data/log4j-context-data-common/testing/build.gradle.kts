plugins {
  id("otel.java-conventions")
}

dependencies {
  api(project(":testing-common"))

  api("org.apache.logging.log4j:log4j-core:2.7")

  implementation("com.google.guava:guava")

  implementation("io.opentelemetry:opentelemetry-api")

  annotationProcessor("org.apache.logging.log4j:log4j-core:2.7")
}
