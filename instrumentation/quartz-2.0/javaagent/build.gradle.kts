import io.opentelemetry.instrumentation.gradle.executions

plugins {
  id("otel.javaagent-instrumentation")
}

muzzle {
  pass {
    group.set("org.quartz-scheduler")
    module.set("quartz")
    versions.set("[2.0.0,)")
    assertInverse.set(true)
  }
}

dependencies {
  implementation(project(":instrumentation:quartz-2.0:library"))

  library("org.quartz-scheduler:quartz:2.0.0")

  testImplementation(project(":instrumentation:quartz-2.0:testing"))
}

testing {
  suites {
    named<JvmTestSuite>("test") {
      executions {
        register("Experimental") {
          jvmArgs("-Dotel.instrumentation.quartz.experimental-span-attributes=true")
          systemProperty("metadataConfig", "otel.instrumentation.quartz.experimental-span-attributes=true")
        }
      }
    }
  }
}
