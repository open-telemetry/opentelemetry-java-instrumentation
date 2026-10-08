plugins {
  id("otel.javaagent-instrumentation")
}

muzzle {
  pass {
    group.set("org.apache.pulsar")
    module.set("pulsar-client")
    versions.set("[2.8.0,5)")
    assertInverse.set(true)
  }
  pass {
    group.set("org.apache.pulsar")
    module.set("pulsar-client-v5-all")
    versions.set("[5.0.0,)")
    extraDependency("io.swagger.core.v3:swagger-annotations:2.2.55")
  }
}

dependencies {
  library("org.apache.pulsar:pulsar-client:2.8.0")

  testImplementation("javax.annotation:javax.annotation-api:1.3.2")
  testImplementation("org.testcontainers:testcontainers-pulsar")
  testLibrary("org.apache.pulsar:pulsar-client-admin:2.8.0")

  // Pulsar 5 uses a different artifact and is covered by version5Test.
  latestDepTestLibrary("org.apache.pulsar:pulsar-client:4.+") // documented limitation
  latestDepTestLibrary("org.apache.pulsar:pulsar-client-admin:4.+") // documented limitation
}

testing {
  suites {
    register<JvmTestSuite>("version5Test") {
      sources {
        java {
          setSrcDirs(listOf("src/test/java", "src/version5Test/java"))
        }
      }
      dependencies {
        implementation("org.apache.pulsar:pulsar-client-v5-all:${baseVersion("5.0.0").orLatest()}")
        implementation("javax.annotation:javax.annotation-api:1.3.2")
        implementation("org.testcontainers:testcontainers-pulsar")
        compileOnly("io.swagger.core.v3:swagger-annotations:2.2.55")
      }
      targets.all {
        testTask.configure {
          filter {
            excludeTestsMatching("PulsarClientSuppressReceiveSpansTest")
          }
          jvmArgs("-Dotel.instrumentation.common.messaging.experimental.receive-telemetry.enabled=true")
          systemProperty("pulsarBrokerImage", "apachepulsar/pulsar:5.0.0")
          systemProperty(
            "metadataConfig",
            "otel.instrumentation.common.messaging.experimental.receive-telemetry.enabled=true",
          )
        }
      }
    }
  }
}

tasks {
  named("compileVersion5TestJava", JavaCompile::class).configure {
    options.release.set(17)
  }
  val testJavaVersion = otelProps.testJavaVersion ?: JavaVersion.current()
  if (!testJavaVersion.isCompatibleWith(JavaVersion.VERSION_17)) {
    named("version5Test", Test::class).configure {
      enabled = false
    }
  }

  withType<Test>().configureEach {
    systemProperty("collectMetadata", otelProps.collectMetadata)
    usesService(gradle.sharedServices.registrations["testcontainersBuildService"].service)
    systemProperty("io.opentelemetry.pulsar-2.8.debug", "true")
  }

  val testReceiveSpanDisabled = register<Test>("testReceiveSpanDisabled") {
    testClassesDirs = sourceSets.test.get().output.classesDirs
    classpath = sourceSets.test.get().runtimeClasspath
    filter {
      includeTestsMatching("PulsarClientSuppressReceiveSpansTest")
    }
    include("**/PulsarClientSuppressReceiveSpansTest.*")
  }

  val testExperimental = register<Test>("testExperimental") {
    testClassesDirs = sourceSets.test.get().output.classesDirs
    classpath = sourceSets.test.get().runtimeClasspath

    filter {
      excludeTestsMatching("PulsarClientSuppressReceiveSpansTest")
    }
    jvmArgs("-Dotel.instrumentation.common.messaging.experimental.receive-telemetry.enabled=true")

    jvmArgs("-Dotel.instrumentation.pulsar.experimental-span-attributes=true")
    systemProperty("metadataConfig", "otel.instrumentation.pulsar.experimental-span-attributes=true")
  }

  test {
    filter {
      excludeTestsMatching("PulsarClientSuppressReceiveSpansTest")
    }
    jvmArgs("-Dotel.instrumentation.common.messaging.experimental.receive-telemetry.enabled=true")
    systemProperty(
      "metadataConfig",
      "otel.instrumentation.common.messaging.experimental.receive-telemetry.enabled=true",
    )
  }

  check {
    dependsOn(
      testing.suites,
      testReceiveSpanDisabled,
      testExperimental,
    )
  }

  if (otelProps.denyUnsafe) {
    withType<Test>().configureEach {
      enabled = false
    }
  }
}
