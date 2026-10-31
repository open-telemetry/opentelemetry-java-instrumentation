import io.opentelemetry.instrumentation.gradle.executions

plugins {
  id("otel.javaagent-instrumentation")
}

muzzle {
  pass {
    group.set("org.apache.shardingsphere.elasticjob")
    module.set("elasticjob-lite-core")
    versions.set("[3.0.0,)")
    assertInverse.set(true)
  }
}

dependencies {
  library("org.apache.shardingsphere.elasticjob:elasticjob-lite-core:3.0.0")

  testImplementation("org.apache.curator:curator-test:5.1.0")
}

testing {
  suites {
    named<JvmTestSuite>("test") {
      executions {
        register("Experimental") {
          jvmArgs("-Dotel.instrumentation.apache-elasticjob.experimental-span-attributes=true")
          systemProperty("metadataConfig", "otel.instrumentation.apache-elasticjob.experimental-span-attributes=true")
        }
      }
    }
  }
}
