import com.github.jengelman.gradle.plugins.shadow.tasks.ShadowJar

plugins {
  id("otel.java-conventions")
}

otelJava {
  minJavaVersionSupported.set(JavaVersion.VERSION_11)
}

val agentShadowJar = project(":javaagent").tasks.named<ShadowJar>("shadowJar")

dependencies {
  testImplementation(project(":smoke-tests"))
  testImplementation(project(":testing-common"))
  testImplementation("io.opentelemetry:opentelemetry-sdk-testing")
  testImplementation("org.apache.kafka:kafka-clients:3.6.1")
  testImplementation("io.opentelemetry:opentelemetry-exporter-logging")
  testImplementation("io.opentelemetry:opentelemetry-exporter-otlp")
  testImplementation(project(":instrumentation:kafka:kafka-clients:kafka-clients-2.6:library"))

  testImplementation("org.testcontainers:testcontainers-postgresql") // For PostgreSQLContainer
  testImplementation("org.postgresql:postgresql:42.7.2") // PostgreSQL JDBC driver
  testImplementation("org.testcontainers:testcontainers-mongodb") // For MongoDBContainer
  testImplementation("org.mongodb:mongodb-driver-sync:4.11.0") // MongoDB Java driver

  // Testcontainers dependencies for integration testing
  testImplementation("org.testcontainers:testcontainers-junit-jupiter")
  testImplementation("org.testcontainers:testcontainers")
  testImplementation("org.testcontainers:testcontainers-kafka")
  testImplementation("io.rest-assured:rest-assured:5.5.5")
  testImplementation("com.fasterxml.jackson.core:jackson-databind")
}

tasks.withType<Test>().configureEach {
  dependsOn(agentShadowJar)
  systemProperty("io.opentelemetry.smoketest.agent.shadowJar.path", agentShadowJar.get().archiveFile.get().toString())
}
