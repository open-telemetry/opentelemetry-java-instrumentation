plugins {
  id("otel.java-conventions")
  id("otel.testcontainers-testing")
}

dependencies {
  api("io.opentelemetry.javaagent:opentelemetry-testing-common")
  implementation("org.testcontainers:testcontainers-oracle-free")

  compileOnly("com.oracle.database.jdbc:ucp:11.2.0.4")
}
