plugins {
  id("otel.javaagent-instrumentation")
  id("otel.nullaway-conventions")
}

dependencies {
  compileOnly(project(":instrumentation-api-incubator"))
  compileOnly("org.springframework:spring-webmvc:3.1.0.RELEASE")
}
