plugins {
  id("otel.java-conventions")
}

val springBootVersion = "2.7.18"

dependencies {
  compileOnly("org.springframework.boot:spring-boot-restclient:4.0.0")
  compileOnly("org.springframework.kafka:spring-kafka:2.9.0")

  compileOnly("org.springframework.boot:spring-boot-starter-test:$springBootVersion")
  compileOnly("org.springframework.boot:spring-boot-starter-data-r2dbc:$springBootVersion")

  compileOnly(project(":instrumentation:micrometer:micrometer-1.5:library"))
  compileOnly(project(":instrumentation:spring:spring-boot-autoconfigure"))
  compileOnly("io.opentelemetry.javaagent:opentelemetry-testing-common")
  compileOnly("io.opentelemetry:opentelemetry-sdk")
  compileOnly("io.opentelemetry:opentelemetry-sdk-testing")
  compileOnly("io.opentelemetry:opentelemetry-sdk-extension-autoconfigure-spi")
}

tasks {
  compileJava {
    // RestClient from spring-boot-restclient:4.0.0 uses Spring Framework 7.0.1
    // which has @Deprecated annotations with Java 9+ attributes (since, forRemoval)
    sourceCompatibility = "17"
    targetCompatibility = "17"
    options.release.set(17)
  }
}
