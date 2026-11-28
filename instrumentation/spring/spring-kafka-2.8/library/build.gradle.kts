import io.opentelemetry.instrumentation.gradle.executions

plugins {
  id("otel.library-instrumentation")
  id("otel.nullaway-conventions")
  id("otel.testcontainers-testing")
}

dependencies {
  compileOnly(project(":muzzle"))

  compileOnly("com.google.auto.value:auto-value-annotations")
  annotationProcessor("com.google.auto.value:auto-value")

  implementation(project(":instrumentation:kafka:kafka-clients:kafka-clients-common-0.11:library"))

  library("org.springframework.kafka:spring-kafka:2.8.0")

  testImplementation(project(":instrumentation:spring:spring-kafka-2.8:testing"))
  testImplementation(project(":instrumentation:kafka:kafka-clients:kafka-clients-2.6:library"))

  testLibrary("org.springframework.boot:spring-boot-starter-test:2.5.3")
  if (otelProps.testLatestDeps) {
    testImplementation("org.springframework.boot:spring-boot-starter-kafka:latest.release")
  } else {
    testLibrary("org.springframework.boot:spring-boot-starter:2.5.3")
  }
}

testing.suites {
  executions("test") {
    configureEach {
      systemProperty("testLatestDeps", otelProps.testLatestDeps)
    }

    configureDefault {
      filter {
        excludeTestsMatching("*.SpringKafkaInterceptorTest")
        excludeTestsMatching("*.SpringKafkaBatchCompletionSkippedTest")
      }
    }

    register("Regression") {
      filter {
        includeTestsMatching("*.SpringKafkaInterceptorTest")
        includeTestsMatching("*.SpringKafkaBatchCompletionSkippedTest")
      }
    }
  }
}

// spring 6 (which spring-kafka 3.+ uses) requires java 17
if (otelProps.testLatestDeps) {
  otelJava {
    minJavaVersionSupported.set(JavaVersion.VERSION_17)
  }
}

// spring 6 uses slf4j 2.0
if (!otelProps.testLatestDeps) {
  configurations.testRuntimeClasspath {
    resolutionStrategy {
      // requires old logback (and therefore also old slf4j)
      force("ch.qos.logback:logback-classic:1.2.11")
      force("org.slf4j:slf4j-api:1.7.36")
    }
  }
}
