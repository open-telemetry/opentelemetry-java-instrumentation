plugins {
  id("otel.java-conventions")
}

dependencies {
  api("io.opentelemetry.javaagent:opentelemetry-testing-common")

  compileOnly("org.apache.hbase:hbase-client:2.0.0")
  compileOnly("com.github.docker-java:docker-java-api:3.3.0")
  compileOnly("org.testcontainers:testcontainers")
}
