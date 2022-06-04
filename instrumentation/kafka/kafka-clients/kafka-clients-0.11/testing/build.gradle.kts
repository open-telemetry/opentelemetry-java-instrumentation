plugins {
  id("otel.java-conventions")
}

dependencies {
  api(project(":testing-common"))

  implementation("org.apache.kafka:kafka-clients:0.11.0.0")

  implementation(project(":instrumentation:kafka:kafka-clients:kafka-clients-common:library"))

  implementation("org.testcontainers:kafka")

  runtimeOnly("org.apache.kafka:kafka_2.13:2.8.1")
  implementation("com.salesforce.kafka.test:kafka-junit5:3.2.3") {
    exclude(module="zookeeper") // conflicts with zookeeper from kafka_2.x
  }
}
