plugins {
  id("otel.javaagent-instrumentation")
}

muzzle {
  pass {
    group.set("org.redisson")
    module.set("redisson")
    versions.set("[2.3.0,3.18.0)")
    // Muzzle passes, but classLoaderMatcher() excludes this pre-2.3 release.
    skip("0.9.0")
    assertInverse.set(true)
  }
}

dependencies {
  library("org.redisson:redisson:2.3.0")

  implementation(project(":instrumentation:redisson:redisson-metrics-common-2.3:javaagent"))
  testInstrumentation(project(":instrumentation:redisson:redisson-metrics-3.18:javaagent"))
  testInstrumentation(project(":instrumentation:redisson:redisson-metrics-3.26:javaagent"))
  testImplementation(project(":instrumentation:redisson:redisson-metrics-common-2.3:testing"))

  latestDepTestLibrary("org.redisson:redisson:3.17.+") // see redisson-metrics-3.18 module
}

testing {
  suites {
    register<JvmTestSuite>("redisson251Test") {
      sources {
        java {
          setSrcDirs(listOf("src/test/java"))
        }
      }

      dependencies {
        compileOnly(project())
        implementation(project(":instrumentation:redisson:redisson-metrics-common-2.3:testing"))
        implementation("org.redisson:redisson:2.5.1")
      }
    }

    register<JvmTestSuite>("redisson311Test") {
      sources {
        java {
          setSrcDirs(listOf("src/test/java"))
        }
      }

      dependencies {
        compileOnly(project())
        implementation(project(":instrumentation:redisson:redisson-metrics-common-2.3:testing"))

        val version = baseVersion("3.11.0").orLatest("3.11.+")
        implementation("org.redisson:redisson:$version")
      }
    }

    register<JvmTestSuite>("redisson315Test") {
      sources {
        java {
          setSrcDirs(listOf("src/test/java"))
        }
      }

      dependencies {
        compileOnly(project())
        implementation(project(":instrumentation:redisson:redisson-metrics-common-2.3:testing"))
        implementation("org.redisson:redisson:3.15.6")
      }
    }
  }
}

tasks {
  withType<Test>().configureEach {
    systemProperty("collectMetadata", otelProps.collectMetadata)
    usesService(gradle.sharedServices.registrations["testcontainersBuildService"].service)
  }

  val stableSemconvSuites = testing.suites.withType(JvmTestSuite::class)
    .map { suite ->
      register<Test>("${suite.name}StableSemconv") {
        testClassesDirs = suite.sources.output.classesDirs
        classpath = suite.sources.runtimeClasspath

        jvmArgs("-Dotel.semconv-stability.opt-in=database")
        systemProperty("metadataConfig", "otel.semconv-stability.opt-in=database")
      }
    }

  check {
    dependsOn(testing.suites, stableSemconvSuites)
  }
}
