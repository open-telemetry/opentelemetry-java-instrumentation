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
  // SpringExtension in spring-test 7 requires JUnit 6
  implementation(enforcedPlatform("org.junit:junit-bom:6.0.1"))

  implementation(project(":instrumentation:spring:spring-security-config-6.0:library"))

  library("org.springframework.security:spring-security-config:6.0.0")
  library("org.springframework.security:spring-security-web:6.0.0")
  library("io.projectreactor:reactor-core:3.5.0")

  testLibrary("org.springframework:spring-test:6.0.0")
  testLibrary("org.springframework:spring-context:6.0.0")
  // can't use testLibrary for now because 6.2.0-M1 is latest and its POM refers to a missing
  // parent POM, switch back to testLibrary when a new version is released
  // testLibrary("jakarta.servlet:jakarta.servlet-api:6.0.0")
  testImplementation("jakarta.servlet:jakarta.servlet-api:6.0.0")
  latestDepTestLibrary("jakarta.servlet:jakarta.servlet-api:6.1.0") // documented limitation
  // remove after 7.0 is released for spring security
  // spring-test 7 requires spring-context 7
  latestDepTestLibrary("org.springframework:spring-context:latest.release")
  latestDepTestLibrary("org.springframework:spring-web:latest.release")
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
