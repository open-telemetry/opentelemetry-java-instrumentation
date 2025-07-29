plugins {
  id("otel.java-conventions")
}

dependencies {
  compileOnly("redis.clients:jedis:1.4.0")
  api(project(":testing-common"))
  implementation("org.testcontainers:testcontainers")
}
