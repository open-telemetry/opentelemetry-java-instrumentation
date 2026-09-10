plugins {
  id("otel.javaagent-instrumentation")
}

muzzle {
  pass {
    group.set("org.redisson")
    module.set("redisson")
    versions.set("[3.17.0,)")
    assertInverse.set(true)
  }
}

dependencies {
  library("org.redisson:redisson:3.17.0")

  implementation(project(":instrumentation:redisson:redisson-common-3.0:javaagent"))

  testInstrumentation(project(":instrumentation:redisson:redisson-3.0:javaagent"))

  testImplementation(project(":instrumentation:redisson:redisson-common-3.0:testing"))
}

testing {
  suites {
    register<JvmTestSuite>("serviceManagerTest") {
      sources {
        java {
          setSrcDirs(listOf("src/test/java"))
        }
      }

      dependencies {
        implementation(project(":instrumentation:redisson:redisson-common-3.0:testing"))
        // a version from the window where redisson routes configuration through ServiceManager
        implementation("org.redisson:redisson:3.24.3")
      }

      targets.all {
        testTask.configure {
          filter {
            includeTestsMatching("*RedissonClientTest.configuredMasterSlaveServerTarget")
            includeTestsMatching("*RedissonClientTest.configuredSingleServerTarget")
          }
          jvmArgs("-Dotel.semconv-stability.opt-in=database")
          systemProperty("metadataConfig", "otel.semconv-stability.opt-in=database")
        }
      }
    }
  }
}

tasks {
  withType<Test>().configureEach {
    systemProperty("testLatestDeps", otelProps.testLatestDeps)
    usesService(gradle.sharedServices.registrations["testcontainersBuildService"].service)
    systemProperty("collectMetadata", otelProps.collectMetadata)
  }

  val testStableSemconv = register<Test>("testStableSemconv") {
    testClassesDirs = sourceSets.test.get().output.classesDirs
    classpath = sourceSets.test.get().runtimeClasspath
    jvmArgs("-Dotel.semconv-stability.opt-in=database")
    systemProperty("metadataConfig", "otel.semconv-stability.opt-in=database")
  }

  check {
    dependsOn(testing.suites, testStableSemconv)
  }

  if (otelProps.denyUnsafe) {
    withType<Test>().configureEach {
      enabled = false
    }
  }
}
