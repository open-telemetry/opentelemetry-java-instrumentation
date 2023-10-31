plugins {
  id("otel.javaagent-instrumentation")
}

muzzle {
  pass {
    group.set("org.springframework.security")
    module.set("spring-security-config")
    versions.set("[6.0.0,]")

    extraDependency("jakarta.servlet:jakarta.servlet-api:6.0.0")
    extraDependency("org.springframework.security:spring-security-web:6.0.0")
    extraDependency("io.projectreactor:reactor-core:3.5.0")
  }
}

dependencies {
  bootstrap(project(":instrumentation:executors:bootstrap"))

  implementation(project(":instrumentation:spring:spring-security-config-6.0:library"))
  implementation("io.opentelemetry.javaagent:opentelemetry-javaagent-extension-api")

  library("org.springframework.security:spring-security-config:6.0.0")
  library("org.springframework.security:spring-security-web:6.0.0")
  library("io.projectreactor:reactor-core:3.5.0")

  testImplementation("org.springframework:spring-test:6.0.0")
  testImplementation("jakarta.servlet:jakarta.servlet-api:6.0.0")
}

otelJava {
  minJavaVersionSupported.set(JavaVersion.VERSION_17)
}

tasks {
  test {
    systemProperty("otel.instrumentation.common.enduser.enabled", "true")
  }
}
