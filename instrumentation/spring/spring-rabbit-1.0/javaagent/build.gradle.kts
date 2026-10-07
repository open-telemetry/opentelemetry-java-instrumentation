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
        implementation("org.springframework.amqp:spring-rabbit:${baseVersion("2.1.7.RELEASE").orLatest()}")
      }
    }

    register<JvmTestSuite>("version11Test") {
      dependencies {
        implementation("io.opentelemetry:opentelemetry-sdk-testing")
        implementation("org.testcontainers:testcontainers")
        implementation("org.springframework.amqp:spring-rabbit:1.1.0.RELEASE")
      }
    }

    register<JvmTestSuite>("version20Test") {
      dependencies {
        implementation("io.opentelemetry:opentelemetry-sdk-testing")
        implementation("org.testcontainers:testcontainers")
        implementation("org.springframework.amqp:spring-rabbit:2.0.1.RELEASE")
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

    // add byte buddy agent for mockito
    configurations.testRuntimeClasspath.get().find { it.name.contains("byte-buddy-agent") }?.apply {
      jvmArgs("-javaagent:$absolutePath")
    }
  }

  val testSpringDisabled = register<Test>("testSpringDisabled") {
    testClassesDirs = sourceSets.test.get().output.classesDirs
    classpath = sourceSets.test.get().runtimeClasspath
    filter {
      includeTestsMatching("*SpringRabbitRegistrationTest")
      includeTestsMatching("*SpringRabbitTemplateTest")
    }
    jvmArgs("-Dotel.instrumentation.spring-rabbit.enabled=false")

    systemProperty(
      "metadataConfig",
      "otel.instrumentation.spring-rabbit.enabled=false",
    )
  }

  check {
    dependsOn(testing.suites, testSpringDisabled)
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
