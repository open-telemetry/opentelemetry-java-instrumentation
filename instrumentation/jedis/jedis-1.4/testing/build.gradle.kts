plugins {
  id("otel.java-conventions")
}

dependencies {
  compileOnly("redis.clients:jedis:1.4.0")
  api("io.opentelemetry.javaagent:opentelemetry-testing-common")
  implementation("org.testcontainers:testcontainers")
}
