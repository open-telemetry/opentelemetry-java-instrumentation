plugins {
  id("otel.javaagent-instrumentation")
  id("otel.nullaway-conventions")
}

muzzle {
  pass {
    group.set("org.springframework.pulsar")
    module.set("spring-pulsar")
    versions.set("[1.0.0,)")
    assertInverse.set(true)
    excludeInstrumentationName("pulsar-2.8")
  }
}

dependencies {
  library("org.springframework.pulsar:spring-pulsar:1.0.0")
  implementation(project(":instrumentation:pulsar:pulsar-2.8:javaagent"))

  testInstrumentation(project(":instrumentation:pulsar:pulsar-2.8:javaagent"))

  testImplementation(project(":instrumentation:spring:spring-pulsar-1.0:testing"))

  testLibrary("org.springframework.boot:spring-boot-starter-test:3.2.4")
  testLibrary("org.springframework.boot:spring-boot-starter:3.2.4")

  if (otelProps.testLatestDeps) {
    testLibrary("org.springframework.boot:spring-boot-starter-pulsar:latest.release")
  }
}

testing {
  suites {
    register<JvmTestSuite>("testReceiveSpansDisabled") {
      dependencies {
        implementation(project(":instrumentation:spring:spring-pulsar-1.0:testing"))

        val springBootVersion = baseVersion("3.2.4").orLatest()
        val springPulsarVersion = baseVersion("1.0.0").orLatest()
        implementation("org.springframework.boot:spring-boot-starter-test:$springBootVersion")
        implementation("org.springframework.boot:spring-boot-starter:$springBootVersion")
        if (otelProps.testLatestDeps) {
          implementation("org.springframework.boot:spring-boot-starter-pulsar:$springPulsarVersion")
        } else {
          implementation("org.springframework.pulsar:spring-pulsar:$springPulsarVersion")
        }
      }

      targets {
        all {
          testTask.configure {
            jvmArgs("-Dotel.instrumentation.pulsar.experimental-span-attributes=true")
            jvmArgs("-Dotel.instrumentation.messaging.experimental.receive-telemetry.enabled=false")
            systemProperty(
              "metadataConfig",
              "otel.instrumentation.pulsar.experimental-span-attributes=true",
            )
          }
        }
      }
    }
  }
}

tasks {
  withType<Test>().configureEach {
    usesService(gradle.sharedServices.registrations["testcontainersBuildService"].service)
    systemProperty("collectMetadata", otelProps.collectMetadata)
  }

  test {
    jvmArgs("-Dotel.instrumentation.pulsar.experimental-span-attributes=false")
    jvmArgs("-Dotel.instrumentation.messaging.experimental.receive-telemetry.enabled=true")
    systemProperty(
      "metadataConfig",
      "otel.instrumentation.messaging.experimental.receive-telemetry.enabled=true",
    )
  }

  val testV3Preview = register<Test>("testV3Preview") {
    testClassesDirs = sourceSets.test.get().output.classesDirs
    classpath = sourceSets.test.get().runtimeClasspath
    jvmArgs("-Dotel.instrumentation.pulsar.experimental-span-attributes=false")
    jvmArgs("-Dotel.instrumentation.messaging.experimental.receive-telemetry.enabled=true")
    jvmArgs("-Dotel.instrumentation.common.v3-preview=true")
    jvmArgs("-Dotel.semconv-stability.preview=messaging")
    systemProperty("metadataConfig", "otel.instrumentation.common.v3-preview=true")
  }

  val testV3PreviewReceiveSpansDisabled = register<Test>("testV3PreviewReceiveSpansDisabled") {
    testClassesDirs = sourceSets["testReceiveSpansDisabled"].output.classesDirs
    classpath = sourceSets["testReceiveSpansDisabled"].runtimeClasspath
    jvmArgs("-Dotel.instrumentation.pulsar.experimental-span-attributes=true")
    jvmArgs("-Dotel.instrumentation.messaging.experimental.receive-telemetry.enabled=false")
    jvmArgs("-Dotel.instrumentation.common.v3-preview=true")
    jvmArgs("-Dotel.semconv-stability.preview=messaging")
    systemProperty("metadataConfig", "otel.instrumentation.common.v3-preview=true")
  }

  check {
    dependsOn(testing.suites, testV3Preview, testV3PreviewReceiveSpansDisabled)
  }

  if (otelProps.denyUnsafe) {
    withType<Test>().configureEach {
      enabled = false
    }
  }
}

// spring 6 requires java 17
otelJava {
  minJavaVersionSupported.set(JavaVersion.VERSION_17)
}
