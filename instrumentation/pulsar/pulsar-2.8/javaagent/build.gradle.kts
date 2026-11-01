import io.opentelemetry.instrumentation.gradle.executions
import io.opentelemetry.instrumentation.gradle.telemetryConfig

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
  latestDepTestLibrary("org.apache.pulsar:pulsar-client:4.+") // see test suite below
  latestDepTestLibrary("org.apache.pulsar:pulsar-client-admin:4.+") // see test suite below
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
            includeTestsMatching("PulsarClientV5Test")
          }
          telemetryConfig("otel.instrumentation.common.messaging.experimental.receive-telemetry.enabled", "true")
        }
      }
    }

    named<JvmTestSuite>("test") {
      executions {
        configureDefault {
          filter {
            excludeTestsMatching("PulsarClientSuppressReceiveSpansTest")
          }
          telemetryConfig("otel.instrumentation.common.messaging.experimental.receive-telemetry.enabled", "true")
        }

        register("ReceiveSpanDisabled") {
          filter {
            includeTestsMatching("PulsarClientSuppressReceiveSpansTest")
          }
          include("**/PulsarClientSuppressReceiveSpansTest.*")
        }

        register("Experimental") {
          filter {
            excludeTestsMatching("PulsarClientSuppressReceiveSpansTest")
          }
          jvmArgs("-Dotel.instrumentation.common.messaging.experimental.receive-telemetry.enabled=true")
          telemetryConfig("otel.instrumentation.pulsar.experimental-span-attributes", "true")
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
    usesService(gradle.sharedServices.registrations["testcontainersBuildService"].service)
    systemProperty("io.opentelemetry.pulsar-2.8.debug", "true")
    if (otelProps.denyUnsafe) {
      enabled = false
    }
  }

  check {
    dependsOn(testing.suites)
  }
}
