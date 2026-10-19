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
      targets.all {
        testTask.configure {
          filter {
            includeTestsMatching("*SpymemcachedTest.storeRejectedAsTooLarge")
          }
        }
      }
      dependencies {
        implementation("net.spy:spymemcached:2.12.3")
        implementation("com.google.guava:guava")
        implementation("org.testcontainers:testcontainers")
      }
    }

    withType<JvmTestSuite>().configureEach {
      val suiteName = name
      executions {
        configureEach {
          usesService(gradle.sharedServices.registrations["testcontainersBuildService"].service)
        }
        if (suiteName == "test") {
          register("Experimental") {
            jvmArgs("-Dotel.instrumentation.spymemcached.experimental-span-attributes=true")
            systemProperty("metadataConfig", "otel.instrumentation.spymemcached.experimental-span-attributes=true")
          }
        }
      }
    }
  }
}

tasks.withType<Test>().configureEach {
  systemProperty("testLatestDeps", otelProps.testLatestDeps || name.startsWith("protocolStatusTest"))
}
