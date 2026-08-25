plugins {
  id("otel.java-conventions")
}

dependencies {
  testImplementation(project(":instrumentation-api-incubator"))
  testImplementation(project(":instrumentation:lettuce:lettuce-4.0:javaagent"))
  testImplementation("biz.paluch.redis:lettuce:4.0.Final")
}
