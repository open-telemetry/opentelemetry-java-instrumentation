plugins {
  id("otel.java-conventions")
}

dependencies {
  api("io.opentelemetry.javaagent:opentelemetry-testing-common")

  compileOnly("org.apache.hbase:hbase-client:1.4.0")
  implementation("org.testcontainers:testcontainers")
}
