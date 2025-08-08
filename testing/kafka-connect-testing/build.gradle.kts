plugins {
  id("otel.java-conventions")
}

dependencies {
  api(project(":testing-common"))
  
  // Kafka Connect dependencies
  testImplementation("org.apache.kafka:connect-runtime:3.6.1")
  testImplementation(project(":instrumentation:kafka:kafka-clients:kafka-clients-0.11:testing"))
  
  // Testcontainers dependencies for integration testing
  testImplementation("org.testcontainers:postgresql")
  testImplementation("org.testcontainers:junit-jupiter")
  testImplementation("org.postgresql:postgresql:42.7.2")
  testImplementation("com.fasterxml.jackson.core:jackson-databind")
  testImplementation("org.awaitility:awaitility")
  testImplementation("com.squareup.okhttp3:okhttp:4.12.0")
  testImplementation("org.testcontainers:testcontainers:1.19.7")
  testImplementation("org.testcontainers:kafka:1.19.7")
  testImplementation("io.rest-assured:rest-assured:5.3.1")
  testImplementation("org.junit.jupiter:junit-jupiter:5.10.2")

}

tasks {
  withType<Test>().configureEach {
    usesService(gradle.sharedServices.registrations["testcontainersBuildService"].service)
    
    systemProperty("testLatestDeps", findProperty("testLatestDeps") as Boolean?)
    
    // Enable experimental span attributes and receive telemetry for comprehensive testing
    jvmArgs("-Dotel.instrumentation.kafka.experimental-span-attributes=true")
    jvmArgs("-Dotel.instrumentation.messaging.experimental.receive-telemetry.enabled=true")
    // Set timeout for integration tests with containers
    systemProperty("junit.jupiter.execution.timeout.default", "5m")
  }
  
  withType<JavaCompile>().configureEach {
    options.compilerArgs.add("-Xlint:-deprecation")
  }
} 
