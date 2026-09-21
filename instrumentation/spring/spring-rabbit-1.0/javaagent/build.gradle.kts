plugins {
  id("otel.javaagent-instrumentation")
  id("otel.nullaway-conventions")
}

muzzle {
  pass {
    group.set("org.springframework.amqp")
    module.set("spring-rabbit")
    versions.set("(,)")
  }
}

dependencies {
  library("org.springframework.amqp:spring-rabbit:1.0.0.RELEASE")

  testInstrumentation(project(":instrumentation:rabbitmq-2.7:javaagent"))

  // 2.1.7 adds the @RabbitListener annotation, we need that for tests
  testLibrary("org.springframework.amqp:spring-rabbit:2.1.7.RELEASE")
  testLibrary("org.springframework.boot:spring-boot-starter-test:1.5.22.RELEASE")
  testLibrary("org.springframework.boot:spring-boot-starter:1.5.22.RELEASE")
  // spring-retry is required by org.springframework.amqp:spring-rabbit:4.0.0
  testLibrary("org.springframework.retry:spring-retry")

  if (otelProps.testLatestDeps) {
    testLibrary("org.springframework.boot:spring-boot-starter-amqp:latest.release")
  }
}

testing {
  suites {
    register<JvmTestSuite>("unitTests") {
      dependencies {
        implementation(project())
        implementation(project(":javaagent-extension-api"))
        implementation("org.springframework.amqp:spring-rabbit:2.1.7.RELEASE")
      }
    }

    register<JvmTestSuite>("version11Test") {
      dependencies {
        implementation("io.opentelemetry:opentelemetry-sdk-testing")
        implementation("org.testcontainers:testcontainers")
        implementation("org.springframework.amqp:spring-rabbit:1.1.0.RELEASE")
      }

      targets {
        all {
          testTask.configure {
            jvmArgs("-Dotel.semconv-stability.preview=messaging")
            systemProperty("metadataConfig", "otel.semconv-stability.preview=messaging")
          }
        }
      }
    }

    register<JvmTestSuite>("version20Test") {
      dependencies {
        implementation("io.opentelemetry:opentelemetry-sdk-testing")
        implementation("org.testcontainers:testcontainers")
        implementation("org.springframework.amqp:spring-rabbit:2.0.1.RELEASE")
      }

      targets {
        all {
          testTask.configure {
            jvmArgs("-Dotel.semconv-stability.preview=messaging")
            systemProperty("metadataConfig", "otel.semconv-stability.preview=messaging")
          }
        }
      }
    }
  }
}

tasks {
  withType<Test>().configureEach {
    if (!name.endsWith("unitTests", ignoreCase = true)) {
      if (name != "testSpringDisabled") {
        usesService(gradle.sharedServices.registrations["testcontainersBuildService"].service)
      }
      systemProperty("collectMetadata", otelProps.collectMetadata)
    }
    systemProperty("testLatestDeps", otelProps.testLatestDeps)
  }

  named<Test>("unitTests") {
    jvmArgs("-Dotel.semconv-stability.preview=messaging")
  }

  val testMessagingPreview = register<Test>("testMessagingPreview") {
    testClassesDirs = sourceSets.test.get().output.classesDirs
    classpath = sourceSets.test.get().runtimeClasspath
    jvmArgs("-Dotel.semconv-stability.preview=messaging")
    systemProperty("metadataConfig", "otel.semconv-stability.preview=messaging")
  }

  val testBothSemconv = register<Test>("testBothSemconv") {
    testClassesDirs = sourceSets.test.get().output.classesDirs
    classpath = sourceSets.test.get().runtimeClasspath
    jvmArgs("-Dotel.semconv-stability.preview=messaging/dup")
    systemProperty("metadataConfig", "otel.semconv-stability.preview=messaging/dup")
  }

  val testV3Preview = register<Test>("testV3Preview") {
    testClassesDirs = sourceSets.test.get().output.classesDirs
    classpath = sourceSets.test.get().runtimeClasspath
    jvmArgs("-Dotel.instrumentation.common.v3-preview=true")
    systemProperty("metadataConfig", "otel.instrumentation.common.v3-preview=true")
  }

  val testSpringDisabled = register<Test>("testSpringDisabled") {
    testClassesDirs = sourceSets.test.get().output.classesDirs
    classpath = sourceSets.test.get().runtimeClasspath
    filter {
      includeTestsMatching("*SpringRabbitRegistrationTest")
      includeTestsMatching("*SpringRabbitTemplateTest")
    }
    jvmArgs("-Dotel.instrumentation.spring-rabbit.enabled=false")
    jvmArgs("-Dotel.semconv-stability.preview=messaging")
    systemProperty(
      "metadataConfig",
      "otel.instrumentation.spring-rabbit.enabled=false,otel.semconv-stability.preview=messaging",
    )
  }

  check {
    dependsOn(testing.suites, testMessagingPreview, testBothSemconv, testV3Preview, testSpringDisabled)
  }
}

// spring 6 requires java 17
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
