plugins {
  id("otel.library-instrumentation")
}

dependencies {
  compileOnly("com.google.auto.value:auto-value-annotations")
  annotationProcessor("com.google.auto.value:auto-value")

  implementation(project(":instrumentation:kafka:kafka-clients:kafka-clients-common:library"))

  compileOnly("org.springframework.kafka:spring-kafka:2.7.0")

  testImplementation(project(":instrumentation:spring:spring-kafka-2.7:testing"))
  testImplementation(project(":instrumentation:kafka:kafka-clients:kafka-clients-2.6:library"))

  // 2.7.0 has a bug that makes decorating a Kafka Producer impossible
  testLibrary("org.springframework.kafka:spring-kafka:2.7.1")

  testLibrary("org.springframework.boot:spring-boot-starter-test:2.5.3")
  testLibrary("org.springframework.boot:spring-boot-starter:2.5.3")
}

configurations.testRuntimeClasspath {
  resolutionStrategy {
    // requires old logback (and therefore also old slf4j)
    force("ch.qos.logback:logback-classic:1.2.11")
    force("org.slf4j:slf4j-api:1.7.36")
  }
}
