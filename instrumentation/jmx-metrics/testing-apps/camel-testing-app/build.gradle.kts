plugins {
  id("otel.java-conventions")
  id("org.springframework.boot")
}

group = "io.opentelemetry.instrumentation.jmx.cameltest"
description = "Application used for Camel JMX metrics testing"

val camelVersion = "4.17.0"

otelJava {
  minJavaVersionSupported.set(JavaVersion.VERSION_17)
}

dependencies {
  implementation("org.springframework.boot:spring-boot-starter-actuator")
  implementation("org.springframework.boot:spring-boot-starter-web")
  implementation("org.apache.camel.springboot:camel-spring-boot-starter:$camelVersion")
  implementation("org.apache.camel.springboot:camel-http-starter:$camelVersion")
  implementation("org.apache.camel.springboot:camel-management-starter:$camelVersion")
  developmentOnly("org.springframework.boot:spring-boot-devtools")
  testImplementation("org.springframework.boot:spring-boot-starter-test")
  testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}
