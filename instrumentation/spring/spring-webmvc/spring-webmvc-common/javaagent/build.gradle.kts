plugins {
  id("otel.javaagent-instrumentation")
}

dependencies {
  compileOnly("org.springframework:spring-webmvc:3.1.0.RELEASE")
}
