import io.opentelemetry.instrumentation.gradle.executions

plugins {
  id("otel.javaagent-instrumentation")
  id("otel.nullaway-conventions")
}

muzzle {
  pass {
    group.set("org.springframework.boot")
    module.set("spring-boot-actuator-autoconfigure")
    versions.set("[2.0.0.RELEASE,)")
    assertInverse.set(true)
    extraDependency("io.micrometer:micrometer-core:1.5.0")
  }
}

dependencies {
  library("org.springframework.boot:spring-boot-actuator-autoconfigure:2.0.0.RELEASE")
  library("io.micrometer:micrometer-core:1.5.0")
  testLibrary("io.micrometer:micrometer-registry-prometheus:1.0.1")

  if (otelProps.testLatestDeps) {
    // Micrometer moved into its own Spring Boot starter in version 4
    testLibrary("org.springframework.boot:spring-boot-starter-micrometer-metrics:4.0.0")
  }

  implementation(project(":instrumentation:micrometer:micrometer-1.5:javaagent"))

  // dependency management pins logback-classic to 1.3 which is the last release that supports java 8
  latestDepTestLibrary("ch.qos.logback:logback-classic:latest.release")
}

testing.suites {
  register<JvmTestSuite>("springBoot3516Test") {
    sources {
      java.setSrcDirs(listOf("src/test/java"))
      resources.setSrcDirs(listOf("src/test/resources"))
    }
    dependencies {
      implementation("org.springframework.boot:spring-boot-starter-actuator:3.5.16")
    }
  }

  executions("springBoot3516Test") {
    configureEach {
      filter {
        includeTestsMatching(
          "io.opentelemetry.javaagent.instrumentation.spring.boot.actuator.autoconfigure.v2_0.ActuatorTest.closingContextDoesNotCloseGlobalRegistry",
        )
      }
      systemProperty("testSpringBoot3516", "true")
      jvmArgs("--add-opens=java.base/java.lang=ALL-UNNAMED")
      jvmArgs("-XX:+IgnoreUnrecognizedVMOptions")
      jvmArgs("-Dotel.instrumentation.spring-boot-actuator-autoconfigure.enabled=true")
      val testJavaVersion = otelProps.testJavaVersion
      // Spring Boot 3.5 requires Java 17.
      enabled =
        enabled &&
        (testJavaVersion == null ||
          testJavaVersion.compareTo(JavaVersion.VERSION_17) >= 0)
    }
  }
}

tasks.test {
  // required on jdk17
  jvmArgs("--add-opens=java.base/java.lang=ALL-UNNAMED")
  jvmArgs("-XX:+IgnoreUnrecognizedVMOptions")

  jvmArgs("-Dotel.instrumentation.spring-boot-actuator-autoconfigure.enabled=true")
}

// spring 6 (spring boot 3) requires java 17
if (otelProps.testLatestDeps) {
  otelJava {
    minJavaVersionSupported.set(JavaVersion.VERSION_17)
  }
}

// spring 6 (spring boot 3) uses slf4j 2.0
if (!otelProps.testLatestDeps) {
  configurations.testRuntimeClasspath {
    resolutionStrategy {
      // requires old logback (and therefore also old slf4j)
      force("ch.qos.logback:logback-classic:1.2.11")
      force("org.slf4j:slf4j-api:1.7.36")
    }
  }
}
