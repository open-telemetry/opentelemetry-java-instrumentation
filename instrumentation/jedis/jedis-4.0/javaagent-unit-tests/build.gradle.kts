plugins {
  id("otel.java-conventions")
}

dependencies {
  testImplementation(project(":instrumentation-api-incubator"))
  testImplementation(project(":instrumentation:jedis:jedis-4.0:javaagent"))
  testImplementation("redis.clients:jedis:4.0.0-beta1")
}
