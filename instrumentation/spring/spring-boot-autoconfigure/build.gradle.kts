plugins {
  id("otel.library-instrumentation")
}

// Name the Spring Boot modules in accordance with https://docs.spring.io/spring-boot/docs/current/reference/html/features.html#features.developing-auto-configuration.custom-starter
base.archivesName.set("opentelemetry-spring-boot")
group = "io.opentelemetry.instrumentation"

val versions: Map<String, String> by project
val springBootVersion = versions["org.springframework.boot"]

dependencies {
  implementation("org.springframework.boot:spring-boot-autoconfigure:$springBootVersion")
  annotationProcessor("org.springframework.boot:spring-boot-autoconfigure-processor:$springBootVersion")
  implementation("javax.validation:validation-api")

  implementation(project(":instrumentation-annotations-support"))
  implementation(project(":instrumentation:kafka:kafka-clients:kafka-clients-2.6:library"))
  implementation(project(":instrumentation:spring:spring-kafka-2.7:library"))
  implementation(project(":instrumentation:spring:spring-web:spring-web-3.1:library"))
  implementation(project(":instrumentation:spring:spring-webmvc:spring-webmvc-5.3:library"))
  implementation(project(":instrumentation:spring:spring-webflux-5.0:library"))
  implementation(project(":instrumentation:micrometer:micrometer-1.5:library"))

  compileOnly("org.springframework.kafka:spring-kafka:2.9.0")
  compileOnly("org.springframework.boot:spring-boot-starter-actuator:$springBootVersion")
  compileOnly("org.springframework.boot:spring-boot-starter-aop:$springBootVersion")
  compileOnly("org.springframework.boot:spring-boot-starter-web:$springBootVersion")
  compileOnly("org.springframework.boot:spring-boot-starter-webflux:$springBootVersion")

  compileOnly("io.opentelemetry:opentelemetry-sdk-extension-autoconfigure-spi")
  compileOnly("io.opentelemetry:opentelemetry-extension-annotations")
  compileOnly("io.opentelemetry:opentelemetry-extension-trace-propagators")
  compileOnly("io.opentelemetry.contrib:opentelemetry-aws-xray-propagator")
  compileOnly("io.opentelemetry:opentelemetry-exporter-logging")
  compileOnly("io.opentelemetry:opentelemetry-exporter-jaeger")
  compileOnly("io.opentelemetry:opentelemetry-exporter-otlp")
  compileOnly("io.opentelemetry:opentelemetry-exporter-zipkin")
  compileOnly(project(":instrumentation-annotations"))

  compileOnly(project(":instrumentation:resources:library"))
  annotationProcessor("com.google.auto.service:auto-service")
  compileOnly("com.google.auto.service:auto-service-annotations")

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
  testImplementation(project(":instrumentation:resources:library"))
  testImplementation("io.opentelemetry:opentelemetry-sdk-extension-autoconfigure-spi")
  testImplementation("io.opentelemetry:opentelemetry-extension-annotations")
  testImplementation("io.opentelemetry:opentelemetry-extension-trace-propagators")
  testImplementation("io.opentelemetry.contrib:opentelemetry-aws-xray-propagator")
  testImplementation("io.opentelemetry:opentelemetry-exporter-logging")
  testImplementation("io.opentelemetry:opentelemetry-exporter-jaeger")
  testImplementation("io.opentelemetry:opentelemetry-exporter-otlp")
  testImplementation("io.opentelemetry:opentelemetry-exporter-zipkin")
  testImplementation(project(":instrumentation-annotations"))
}

tasks.compileTestJava {
  options.compilerArgs.add("-parameters")
}

tasks.withType<Test>().configureEach {
  // required on jdk17
  jvmArgs("--add-opens=java.base/java.lang=ALL-UNNAMED")
  jvmArgs("-XX:+IgnoreUnrecognizedVMOptions")

  // disable tests on openj9 18 because they often crash JIT compiler
  val testJavaVersion = gradle.startParameter.projectProperties["testJavaVersion"]?.let(JavaVersion::toVersion)
  val testOnOpenJ9 = gradle.startParameter.projectProperties["testJavaVM"]?.run { this == "openj9" }
    ?: false
  if (testOnOpenJ9 && testJavaVersion?.majorVersion == "18") {
    enabled = false
  }
}