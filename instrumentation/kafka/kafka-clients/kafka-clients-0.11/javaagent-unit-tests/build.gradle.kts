plugins {
  id("otel.java-conventions")
}

dependencies {
  testImplementation(project(":instrumentation:kafka:kafka-clients:kafka-clients-0.11:bootstrap"))
  testImplementation(project(":instrumentation:kafka:kafka-clients:kafka-clients-0.11:javaagent"))
  testImplementation("org.apache.kafka:kafka-clients:0.11.0.0")
}
