plugins {
  id("otel.java-conventions")
}

dependencies {
  testImplementation(project(":javaagent-extension-api"))
  testImplementation(project(":instrumentation-api-incubator"))
  testImplementation(project(":instrumentation:jedis:jedis-3.0:javaagent"))
  testImplementation("redis.clients:jedis:3.0.0")
}
