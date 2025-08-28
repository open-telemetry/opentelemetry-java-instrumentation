plugins {
  id("otel.javaagent-instrumentation")
}

muzzle {
  pass {
    group.set("com.oracle.database.jdbc")
    module.set("ucp")
    versions.set("[,)")
    assertInverse.set(true)
  }
}

dependencies {
  library("com.oracle.database.jdbc:ucp:11.2.0.4")
  library("com.oracle.database.jdbc:ojdbc8:12.2.0.1")

  implementation(project(":instrumentation:oracle-ucp-11.2:library"))

  testImplementation(project(":instrumentation:oracle-ucp-11.2:testing"))
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
    usesService(gradle.sharedServices.registrations["testcontainersBuildService"].service)
    systemProperty("collectMetadata", collectMetadata)
  }

  check {
    dependsOn(testing.suites.named("testStableSemconv"))
  }
}
