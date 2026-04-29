plugins {
  id("otel.java-conventions")
  alias(springBoot31.plugins.versions)
  id("otel.spring-native-test-conventions")
}

description = "smoke-tests-otel-starter-spring-boot-reactive-3"

otelJava {
  minJavaVersionSupported.set(JavaVersion.VERSION_17)
}

dependencies {
  implementation(project(":instrumentation:spring:starters:spring-boot-starter"))
  implementation(platform(org.springframework.boot.gradle.plugin.SpringBootPlugin.BOM_COORDINATES))

  implementation(project(":smoke-tests-otel-starter:spring-boot-reactive-common"))
  implementation("org.springframework.boot:spring-boot-starter-webflux")
  implementation("org.springframework.boot:spring-boot-starter-data-r2dbc")

  runtimeOnly("com.h2database:h2")
  runtimeOnly("io.r2dbc:r2dbc-h2")

  testImplementation("org.springframework.boot:spring-boot-starter-test")
  testImplementation("io.projectreactor:reactor-test")
}

springBoot {
  mainClass = "io.opentelemetry.spring.smoketest.OtelReactiveSpringStarterSmokeTestApplication"
}

tasks {
  bootJar {
    enabled = false
  }
}
