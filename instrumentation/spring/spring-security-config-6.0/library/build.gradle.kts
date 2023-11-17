plugins {
  id("otel.library-instrumentation")
}

dependencies {
  library("org.springframework.security:spring-security-config:6.0.0")
  library("org.springframework.security:spring-security-web:6.0.0")
  library("org.springframework:spring-web:6.0.0")
  library("io.projectreactor:reactor-core:3.5.0")
  library("jakarta.servlet:jakarta.servlet-api:6.0.0")

  implementation(project(":instrumentation:reactor:reactor-3.1:library"))

  testImplementation(project(":testing-common"))
  testLibrary("org.springframework:spring-test:6.0.0")
}

otelJava {
  minJavaVersionSupported.set(JavaVersion.VERSION_17)
}
