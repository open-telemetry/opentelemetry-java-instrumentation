plugins {
  id("otel.java-conventions")
  id("otel.publish-conventions")
}

group = "io.opentelemetry.instrumentation"

val versions: Map<String, String> by project
val springBootVersion = versions["org.springframework.boot"]

dependencies {
  implementation("org.springframework.boot:spring-boot-autoconfigure:$springBootVersion")
  annotationProcessor("org.springframework.boot:spring-boot-autoconfigure-processor:$springBootVersion")
  implementation("javax.validation:validation-api:2.0.1.Final")

  implementation(project(":instrumentation-annotations-support"))
  implementation(project(":instrumentation:kafka:kafka-clients:kafka-clients-2.6:library"))
  implementation(project(":instrumentation:spring:spring-kafka-2.7:library"))
  implementation(project(":instrumentation:spring:spring-web-3.1:library"))
  implementation(project(":instrumentation:spring:spring-webmvc-3.1:library"))
  implementation(project(":instrumentation:spring:spring-webflux-5.0:library"))
  implementation("io.opentelemetry:opentelemetry-micrometer1-shim") {
    // just get the instrumentation, without micrometer itself
    exclude("io.micrometer", "micrometer-core")
  }

  compileOnly("org.springframework.kafka:spring-kafka:2.9.0")
  compileOnly("org.springframework.boot:spring-boot-starter-actuator:$springBootVersion")
  compileOnly("org.springframework.boot:spring-boot-starter-aop:$springBootVersion")
  compileOnly("org.springframework.boot:spring-boot-starter-web:$springBootVersion")
  compileOnly("org.springframework.boot:spring-boot-starter-webflux:$springBootVersion")

  compileOnly("io.opentelemetry:opentelemetry-sdk-extension-autoconfigure-spi")
  compileOnly("io.opentelemetry:opentelemetry-extension-annotations")
  compileOnly("io.opentelemetry:opentelemetry-extension-trace-propagators")
  compileOnly("io.opentelemetry:opentelemetry-extension-aws")
  compileOnly("io.opentelemetry:opentelemetry-sdk-extension-resources")
  compileOnly("io.opentelemetry:opentelemetry-exporter-logging")
  compileOnly("io.opentelemetry:opentelemetry-exporter-jaeger")
  compileOnly("io.opentelemetry:opentelemetry-exporter-otlp")
  compileOnly("io.opentelemetry:opentelemetry-exporter-zipkin")
  compileOnly(project(":instrumentation-annotations"))

  testImplementation("org.springframework.kafka:spring-kafka:2.9.0")
  testImplementation("org.springframework.boot:spring-boot-starter-actuator:$springBootVersion")
  testImplementation("org.springframework.boot:spring-boot-starter-aop:$springBootVersion")
  testImplementation("org.springframework.boot:spring-boot-starter-webflux:$springBootVersion")
  testImplementation("org.springframework.boot:spring-boot-starter-web:$springBootVersion")
  testImplementation("org.springframework.boot:spring-boot-starter-test:$springBootVersion") {
    exclude("org.junit.vintage", "junit-vintage-engine")
  }
  testImplementation("org.testcontainers:kafka")

  testImplementation(project(":testing-common"))
  testImplementation("io.opentelemetry:opentelemetry-sdk")
  testImplementation("io.opentelemetry:opentelemetry-sdk-testing")
  testImplementation("io.opentelemetry:opentelemetry-sdk-extension-resources")
  testImplementation("io.opentelemetry:opentelemetry-sdk-extension-autoconfigure-spi")
  testImplementation("io.opentelemetry:opentelemetry-extension-annotations")
  testImplementation("io.opentelemetry:opentelemetry-extension-trace-propagators")
  testImplementation("io.opentelemetry:opentelemetry-extension-aws")
  testImplementation("io.opentelemetry:opentelemetry-exporter-logging")
  testImplementation("io.opentelemetry:opentelemetry-exporter-jaeger")
  testImplementation("io.opentelemetry:opentelemetry-exporter-otlp")
  testImplementation("io.opentelemetry:opentelemetry-exporter-zipkin")
  testImplementation(project(":instrumentation-annotations"))
}

tasks.compileTestJava {
  options.compilerArgs.add("-parameters")
}
