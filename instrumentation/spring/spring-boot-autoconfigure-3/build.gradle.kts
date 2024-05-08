plugins {
  id("otel.library-instrumentation")
}

// Name the Spring Boot modules in accordance with https://docs.spring.io/spring-boot/docs/current/reference/html/features.html#features.developing-auto-configuration.custom-starter
base.archivesName.set("opentelemetry-spring-boot-3")
group = "io.opentelemetry.instrumentation"

otelJava {
  minJavaVersionSupported.set(JavaVersion.VERSION_17)
}

dependencies {
  val springBootVersion = "3.2.4"
  library("org.springframework.boot:spring-boot-starter-web:$springBootVersion")
  compileOnly(project(":instrumentation:spring:spring-boot-autoconfigure"))
  implementation(project(":instrumentation:spring:spring-web:spring-web-3.1:library"))

  testImplementation(project(":instrumentation:spring:spring-boot-autoconfigure"))
  testLibrary("org.springframework.boot:spring-boot-starter-test:$springBootVersion") {
    exclude("org.junit.vintage", "junit-vintage-engine")
  }
}
