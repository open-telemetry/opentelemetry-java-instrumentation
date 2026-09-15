plugins {
  id("otel.java-conventions")
}

dependencies {
  testImplementation(project(":instrumentation-api-incubator"))
  testImplementation(project(":instrumentation:jedis:jedis-1.4:javaagent"))
  testImplementation("redis.clients:jedis:1.4.0")
}
