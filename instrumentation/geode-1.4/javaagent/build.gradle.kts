plugins {
  id("otel.javaagent-instrumentation")
}

muzzle {
  pass {
    group.set("org.apache.geode")
    module.set("geode-core")
    versions.set("[1.4.0,)")
  }
}

dependencies {
  library("org.apache.geode:geode-core:1.4.0")

  compileOnly("com.google.auto.value:auto-value-annotations")
  annotationProcessor("com.google.auto.value:auto-value")
}

val collectMetadata = findProperty("collectMetadata")?.toString() ?: "false"

testing {
  suites {
    val testStableSemconv by registering(JvmTestSuite::class) {
      targets {
        all {
          testTask.configure {
            jvmArgs("-Dotel.semconv-stability.opt-in=database")
            systemProperty("collectMetadata", collectMetadata)
            systemProperty("metadataConfig", "otel.semconv-stability.opt-in=database")
          }
        }
      }
    }
  }
}

tasks {
  test {
    systemProperty("collectMetadata", collectMetadata)
  }

  check {
    dependsOn(testing.suites)
  }
}
