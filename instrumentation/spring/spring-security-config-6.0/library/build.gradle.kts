plugins {
  id("otel.library-instrumentation")
}

dependencies {
  library("org.springframework.security:spring-security-config:6.0.0")
  library("org.springframework.security:spring-security-web:6.0.0")
  library("org.springframework:spring-web:6.0.0")
  library("io.projectreactor:reactor-core:3.5.0")
  // can't use library for now because 6.2.0-M1 is latest and its POM referes to a missing parent POM
  // switch back to library when a new version is released
  // library("jakarta.servlet:jakarta.servlet-api:6.0.0")
  compileOnly("jakarta.servlet:jakarta.servlet-api:6.0.0")
  testImplementation("jakarta.servlet:jakarta.servlet-api:6.0.0")
  latestDepTestLibrary("jakarta.servlet:jakarta.servlet-api:6.1.0") // documented limitation

  implementation(project(":instrumentation:reactor:reactor-3.1:library"))

  testImplementation(project(":testing-common"))
  testLibrary("org.springframework:spring-test:6.0.0")
}

otelJava {
  minJavaVersionSupported.set(JavaVersion.VERSION_17)
}

tasks.test {
  systemProperty("metadataConfig", "otel.instrumentation.common.enduser.id.enabled=true,otel.instrumentation.common.enduser.role.enabled=true,otel.instrumentation.common.enduser.scope.enabled=true")
  systemProperty("collectMetadata", findProperty("collectMetadata")?.toString() ?: "false")
}
