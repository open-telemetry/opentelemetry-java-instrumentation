plugins {
  id("otel.java-conventions")
  alias(springBoot40.plugins.versions)
  id("org.graalvm.buildtools.native")
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

  testImplementation("org.springframework.boot:spring-boot-resttestclient")
  testImplementation("org.springframework.boot:spring-boot-starter-test")
  testImplementation("org.springframework.boot:spring-boot-starter-data-mongodb")
  testImplementation(project(":instrumentation:spring:starters:spring-boot-starter"))
  testImplementation(project(":smoke-tests-otel-starter:spring-smoke-testing"))
  testImplementation("org.springframework.boot:spring-boot-starter-kafka")
  testImplementation("org.testcontainers:testcontainers-junit-jupiter")
  testImplementation("org.testcontainers:testcontainers-kafka")
  testImplementation("org.testcontainers:testcontainers-mongodb")
}

springBoot {
  mainClass = "io.opentelemetry.spring.smoketest.OtelSpringStarterSmokeTestApplication"
}

// Disable -Werror for Spring Framework 7.0 compatibility
tasks.withType<JavaCompile>().configureEach {
  options.compilerArgs.removeAll(listOf("-Werror"))
}

tasks {
  compileAotJava {
    with(options) {
      compilerArgs.add("-Xlint:-deprecation,-unchecked,none")
      // To disable warnings/failure coming from the Java compiler during the Spring AOT processing
      // -deprecation,-unchecked and none are required (none is not enough)
    }
  }
  compileAotTestJava {
    with(options) {
      compilerArgs.add("-Xlint:-deprecation,-unchecked,none")
      // To disable warnings/failure coming from the Java compiler during the Spring AOT processing
      // -deprecation,-unchecked and none are required (none is not enough)
    }
  }
  checkstyleAot {
    isEnabled = false
  }
  checkstyleAotTest {
    isEnabled = false
  }
  bootJar {
    enabled = false
  }
  test {
    usesService(gradle.sharedServices.registrations["testcontainersBuildService"].service)
  }
}

graalvmNative {
  // See https://github.com/graalvm/native-build-tools/issues/572
  metadataRepository {
    enabled.set(false)
  }

  tasks.test {
    useJUnitPlatform()
    setForkEvery(1)
  }
}

// Disable collectReachabilityMetadata task to avoid configuration isolation issues
// See https://github.com/gradle/gradle/issues/17559
tasks.named("collectReachabilityMetadata").configure {
  enabled = false
}
