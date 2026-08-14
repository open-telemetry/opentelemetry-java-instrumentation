plugins {
  id("otel.java-conventions")
}

dependencies {
  testImplementation(project(":instrumentation-api"))
  testImplementation(project(":instrumentation-api-incubator"))
  testImplementation(project(":instrumentation:rocketmq:rocketmq-client-5.0:javaagent"))
  testImplementation("org.apache.rocketmq:rocketmq-client-java:5.0.0")
}

tasks.test {
  jvmArgs("-Dotel.semconv-stability.preview=messaging")
}
