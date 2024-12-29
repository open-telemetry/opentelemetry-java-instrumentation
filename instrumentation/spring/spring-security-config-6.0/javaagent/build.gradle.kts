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
  implementation(project(":instrumentation:spring:spring-security-config-6.0:library"))

  library("org.springframework.security:spring-security-config:6.0.0")
  library("org.springframework.security:spring-security-web:6.0.0")
  library("io.projectreactor:reactor-core:3.5.0")

  testLibrary("org.springframework:spring-test:6.0.0")
  testLibrary("org.springframework:spring-context:6.0.0")
  testLibrary("jakarta.servlet:jakarta.servlet-api:6.0.0")
}

otelJava {
  minJavaVersionSupported.set(JavaVersion.VERSION_17)
}

tasks {
  test {
    systemProperty("otel.instrumentation.common.enduser.id.enabled", "true")
    systemProperty("otel.instrumentation.common.enduser.role.enabled", "true")
    systemProperty("otel.instrumentation.common.enduser.scope.enabled", "true")
  }
}
