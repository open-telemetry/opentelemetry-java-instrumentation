plugins {
  id("otel.java-conventions")
  alias(springBoot40.plugins.versions)
  id("otel.spring-native-test-conventions")
}

description = "smoke-tests-otel-starter-spring-boot-4"

otelJava {
  minJavaVersionSupported.set(JavaVersion.VERSION_17)
}

dependencies {
  implementation("org.springframework.boot:spring-boot-starter-web")
  implementation("org.springframework.boot:spring-boot-starter-data-jdbc")
  implementation("org.springframework.boot:spring-boot-starter-kafka")
  implementation("org.springframework.boot:spring-boot-starter-data-mongodb")
  implementation("org.springframework:spring-aop")
  implementation("org.aspectj:aspectjweaver")
  implementation("org.apache.commons:commons-dbcp2")
  runtimeOnly("com.h2database:h2")
  implementation(platform(org.springframework.boot.gradle.plugin.SpringBootPlugin.BOM_COORDINATES))
  implementation("io.opentelemetry:opentelemetry-extension-trace-propagators")

  testImplementation(project(":smoke-tests-otel-starter:spring-boot-common"))

  testImplementation("org.springframework:spring-test:7.0.7")
  testImplementation("org.springframework.boot:spring-boot-resttestclient")
  testImplementation("org.springframework.boot:spring-boot-restclient")
  testImplementation("org.springframework.boot:spring-boot-starter-test")
  testImplementation("org.springframework.boot:spring-boot-starter-data-mongodb")
  testImplementation(project(":instrumentation:spring:starters:spring-boot-starter"))
  testImplementation(project(":smoke-tests-otel-starter:spring-smoke-testing"))
  testImplementation("org.springframework.boot:spring-boot-starter-kafka")
  testImplementation("org.testcontainers:testcontainers-junit-jupiter")
  testImplementation("org.testcontainers:testcontainers-kafka")
  testImplementation("org.testcontainers:testcontainers-mongodb")
  testImplementation(project(":instrumentation:spring:spring-boot-autoconfigure"))
}

springBoot {
  mainClass = "io.opentelemetry.spring.smoketest.OtelSpringStarterSmokeTestApplication"
}

// Disable -Werror for Spring Framework 7.0 compatibility
tasks.withType<JavaCompile>().configureEach {
  options.compilerArgs.removeAll(listOf("-Werror"))
}

tasks {
  bootJar {
    enabled = false
  }
  test {
    usesService(gradle.sharedServices.registrations["testcontainersBuildService"].service)
  }
}

plugins.withId("org.graalvm.buildtools.native") {
  // Spring Boot 4 requires GraalVM native-image with Java 25+ support
  // Disable native test tasks if running on Java < 25
  val javaVersionSupportsNative = JavaVersion.current().isCompatibleWith(JavaVersion.VERSION_25)
  tasks.named("nativeTest").configure {
    enabled = javaVersionSupportsNative
  }
  tasks.named("nativeTestCompile").configure {
    enabled = javaVersionSupportsNative
  }
}
