plugins {
  id("otel.java-conventions")
}

dependencies {
  api("io.opentelemetry.javaagent:opentelemetry-instrumentation-testing")

  compileOnly("org.apache.hbase:hbase-client:1.0.0")
  implementation("org.testcontainers:testcontainers")
}
