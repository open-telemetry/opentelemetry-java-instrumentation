plugins {
  id("otel.javaagent-instrumentation")
  id("otel.nullaway-conventions")
}

dependencies {
  implementation(
    project(":instrumentation:spring:spring-cloud-gateway:spring-cloud-gateway-common:library")
  )
}
