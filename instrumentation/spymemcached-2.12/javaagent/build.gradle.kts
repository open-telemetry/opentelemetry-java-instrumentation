import io.opentelemetry.instrumentation.gradle.executions
import io.opentelemetry.instrumentation.gradle.telemetryConfig

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

  testImplementation("com.google.guava:guava:33.7.2-jre")
}

testing {
  suites {
    register<JvmTestSuite>("unitTests") {
      dependencies {
        implementation(project())
        implementation("net.spy:spymemcached:2.12.0")
      }
    }

    named<JvmTestSuite>("test") {
      executions {
        configureEach {
          usesService(gradle.sharedServices.registrations["testcontainersBuildService"].service)
          systemProperty("testLatestDeps", otelProps.testLatestDeps)
        }

        register("Experimental") {
          telemetryConfig("otel.instrumentation.spymemcached.experimental-span-attributes", "true")
        }
      }
    }
  }
}

tasks.check {
  dependsOn(testing.suites)
}
