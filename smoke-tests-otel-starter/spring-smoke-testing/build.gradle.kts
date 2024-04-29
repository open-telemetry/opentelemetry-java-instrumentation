plugins {
  id("otel.java-conventions")
}

otelJava {
  minJavaVersionSupported.set(JavaVersion.VERSION_17)
}

val versions: Map<String, String> by project
val springBootVersion = versions["org.springframework.boot"]

dependencies {
  compileOnly("org.springframework.boot:spring-boot-starter:$springBootVersion")
  api(project(":testing-common"))
  api(project(":instrumentation:spring:spring-boot-autoconfigure"))
  api("io.opentelemetry:opentelemetry-sdk-extension-autoconfigure-spi")
}
