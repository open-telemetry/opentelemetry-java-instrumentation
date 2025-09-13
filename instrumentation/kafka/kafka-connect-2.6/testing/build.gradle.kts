import com.github.jengelman.gradle.plugins.shadow.tasks.ShadowJar

plugins {
  id("otel.java-conventions")
}

// Smoke test pattern - get the agent jar
val agentShadowJar = project(":javaagent").tasks.named<ShadowJar>("shadowJar")

dependencies {
  // Add testing-common manually since we removed otel.javaagent-testing plugin
  testImplementation(project(":testing-common"))
  // Add SDK testing assertions for structured trace verification
  testImplementation("io.opentelemetry:opentelemetry-sdk-testing")
  testImplementation("org.apache.kafka:kafka-clients:3.6.1")

  testImplementation("org.testcontainers:postgresql:1.21.3") // For PostgreSQLContainer
  testImplementation("org.postgresql:postgresql:42.7.2") // PostgreSQL JDBC driver
  testImplementation("org.testcontainers:mongodb:1.21.3") // For MongoDBContainer
  testImplementation("org.mongodb:mongodb-driver-sync:4.11.0") // MongoDB Java driver
  testImplementation("org.apache.httpcomponents:httpclient") // For HttpStatus (not httpcore)

  testImplementation("org.mockito:mockito-junit-jupiter:4.11.0")

  // Testcontainers dependencies for integration testing
  testImplementation("org.testcontainers:junit-jupiter")
  testImplementation("org.awaitility:awaitility")
  testImplementation("com.squareup.okhttp3:okhttp:4.12.0")
  testImplementation("org.testcontainers:testcontainers:1.19.7")
  testImplementation("org.testcontainers:kafka:1.19.7")
  testImplementation("io.rest-assured:rest-assured:5.5.5")
  testImplementation("org.junit.jupiter:junit-jupiter:5.10.2")
  testImplementation("com.fasterxml.jackson.core:jackson-databind")
}

// Configure test tasks to have access to agent jar
tasks.withType<Test>().configureEach {
  dependsOn(agentShadowJar)
  
  // Make agent jar path available to tests  
  systemProperty("io.opentelemetry.smoketest.agent.shadowJar.path", agentShadowJar.get().archiveFile.get().toString())
  
  // Configure test JVM (no agent attached to test process)
  jvmArgs(
    "-Dotel.traces.exporter=none",
    "-Dotel.metrics.exporter=none",
    "-Dotel.logs.exporter=none"
  )
}
