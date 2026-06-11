plugins {
  id("otel.java-conventions")
}

dependencies {
  api("io.opentelemetry.javaagent:opentelemetry-testing-common")

  compileOnly("org.apache.hbase:hbase-client:2.0.0")
  implementation("org.testcontainers:testcontainers")
}
