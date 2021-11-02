plugins {
  id("otel.java-conventions")
}

dependencies {
  api(project(":testing-common"))

  api("io.opentelemetry:opentelemetry-sdk-logs:1.8.0-alpha-SNAPSHOT")

  api("org.apache.logging.log4j:log4j-core:2.7")

  implementation("com.google.guava:guava")

  implementation("org.codehaus.groovy:groovy-all")
  implementation("io.opentelemetry:opentelemetry-api")
  implementation("org.spockframework:spock-core")

  annotationProcessor("org.apache.logging.log4j:log4j-core:2.7")
}
