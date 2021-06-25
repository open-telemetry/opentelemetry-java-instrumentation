plugins {
  id("otel.java-conventions")
  id("otel.publish-conventions")
}

ext {
  springbootVersion = "2.3.1.RELEASE"
}

group = 'io.opentelemetry.instrumentation'

dependencies {
  api group: "org.springframework.boot", name: "spring-boot-starter", version: versions["org.springframework.boot"]
  api project(':instrumentation:spring:starters:spring-starter')
  api "io.opentelemetry:opentelemetry-exporter-jaeger"
  implementation "io.grpc:grpc-netty-shaded:1.30.2"
}

