import io.opentelemetry.instrumentation.gradle.executions

plugins {
  id("otel.javaagent-instrumentation")
}

muzzle {
  pass {
    group.set("net.spy")
    module.set("spymemcached")
    versions.set("[2.12.0,)")
    assertInverse.set(true)
  }
}

dependencies {
  library("net.spy:spymemcached:2.12.0")

  compileOnly("com.google.auto.value:auto-value-annotations")
  annotationProcessor("com.google.auto.value:auto-value")

  testImplementation("com.google.guava:guava")
}

testing {
  suites {
    register<JvmTestSuite>("protocolStatusTest") {
      sources.java.setSrcDirs(listOf("src/test/java"))
      dependencies {
        implementation("net.spy:spymemcached:2.12.3")
        implementation("com.google.guava:guava")
        implementation("org.testcontainers:testcontainers")
      }
    }

    withType<JvmTestSuite>().configureEach {
      executions {
        configureEach {
          usesService(gradle.sharedServices.registrations["testcontainersBuildService"].service)
        }
        register("Experimental") {
          jvmArgs("-Dotel.instrumentation.spymemcached.experimental-span-attributes=true")
          systemProperty("metadataConfig", "otel.instrumentation.spymemcached.experimental-span-attributes=true")
        }
        register("ExceptionSignalLogs") {
          filter {
            includeTestsMatching("*SpymemcachedTest.storeRejectedAsTooLarge")
          }
          jvmArgs("-Dotel.semconv.exception.signal.preview=logs")
        }
      }
    }
  }
}

tasks.withType<Test>().configureEach {
  systemProperty("testLatestDeps", otelProps.testLatestDeps || name.startsWith("protocolStatusTest"))
}
