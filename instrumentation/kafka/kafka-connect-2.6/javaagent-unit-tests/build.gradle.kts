plugins {
  id("otel.java-conventions")
}

dependencies {
  testImplementation(project(":instrumentation:kafka:kafka-connect-2.6:javaagent"))
  testImplementation(project(":instrumentation:kafka:kafka-clients:kafka-clients-common-0.11:library"))
  testImplementation("org.apache.kafka:connect-api:2.6.0")
}
