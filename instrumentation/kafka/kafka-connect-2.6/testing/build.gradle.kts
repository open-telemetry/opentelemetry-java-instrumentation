plugins {
  id("otel.java-conventions")
  id("otel.javaagent-testing")
}

dependencies {
  api(project(":testing-common"))

  implementation("org.apache.kafka:kafka-clients:0.11.0.0")

  implementation(project(":instrumentation:kafka:kafka-clients:kafka-clients-common-0.11:library"))
  implementation(project(":instrumentation:kafka:kafka-connect-2.6:javaagent"))
  implementation(project(":instrumentation:kafka:kafka-clients:kafka-clients-0.11:javaagent"))

  testImplementation("org.apache.kafka:connect-runtime:3.6.1")
  testImplementation(project(":instrumentation:kafka:kafka-clients:kafka-clients-0.11:testing"))

  implementation("org.testcontainers:postgresql:1.21.3") // For PostgreSQLContainer
  testImplementation("org.postgresql:postgresql:42.7.2") // PostgreSQL JDBC driver
  implementation("org.testcontainers:mongodb:1.21.3") // For MongoDBContainer
  testImplementation("org.mongodb:mongodb-driver-sync:4.11.0") // MongoDB Java driver
  testImplementation("org.apache.httpcomponents:httpclient") // For HttpStatus (not httpcore)

  testImplementation("org.mockito:mockito-junit-jupiter:4.11.0")

  // Testcontainers dependencies for integration testing
  testImplementation("io.strimzi:strimzi-test-container:0.111.0")
  implementation("org.testcontainers:junit-jupiter")
  testImplementation("org.awaitility:awaitility")
  testImplementation("com.squareup.okhttp3:okhttp:4.12.0")
  testImplementation("org.testcontainers:testcontainers:1.19.7")
  testImplementation("org.testcontainers:kafka:1.19.7")
  implementation("io.rest-assured:rest-assured:5.5.5")
  testImplementation("org.junit.jupiter:junit-jupiter:5.10.2")
  testImplementation("org.testcontainers:junit-jupiter")
  implementation("com.fasterxml.jackson.core:jackson-databind")
  testImplementation("com.squareup.okhttp3:okhttp:4.12.0")

  compileOnly("com.google.auto.value:auto-value-annotations")
  annotationProcessor("com.google.auto.value:auto-value")
}
