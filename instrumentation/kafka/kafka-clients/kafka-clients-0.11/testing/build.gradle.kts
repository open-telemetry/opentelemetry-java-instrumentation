plugins {
  id("otel.java-conventions")
}

dependencies {
  api(project(":testing-common"))

  implementation("org.apache.kafka:kafka-clients:0.11.0.0")

  implementation(project(":instrumentation:kafka:kafka-clients:kafka-clients-common:library"))

  implementation("org.testcontainers:kafka")
  implementation("org.testcontainers:junit-jupiter")

  runtimeOnly("org.apache.kafka:kafka_2.13:2.8.1")
}
