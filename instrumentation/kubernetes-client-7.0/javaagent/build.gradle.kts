plugins {
  id("otel.javaagent-instrumentation")
}

muzzle {
  pass {
    group.set("io.kubernetes")
    module.set("client-java-api")
    versions.set("[7.0.0,)")
    assertInverse.set(true)
  }
}

dependencies {
  library("io.kubernetes:client-java-api:7.0.0")

  testInstrumentation(project(":instrumentation:okhttp:okhttp-3.0:javaagent"))

  latestDepTestLibrary("io.kubernetes:client-java-api:19.+") // see test suite below
}

val testJavaVersion = otelProps.testJavaVersion ?: JavaVersion.current()

testing {
  suites {
    // version22Test reuses the same test source against `latest.release` in latest-deps mode
    // (currently 26.x), and against 22.0.0 otherwise, to exercise the upper end of the v20+
    // builder API line.
    val version22Test by registering(JvmTestSuite::class) {
      sources {
        java {
          setSrcDirs(listOf("src/version20Test/java"))
        }
      }
      dependencies {
        implementation("io.kubernetes:client-java-api:${baseVersion("22.0.0").orLatest()}")
      }
      targets {
        all {
          testTask.configure {
            // client-java-api 22.0.0+ requires Java 11+
            if (testJavaVersion.isJava8) {
              enabled = false
            }
          }
        }
      }
    }
  }
}

tasks {
  check {
    dependsOn(testing.suites)
  }
}

tasks {
  withType<Test>().configureEach {
    systemProperty("collectMetadata", otelProps.collectMetadata)
  }

  val testExperimental by registering(Test::class) {
    testClassesDirs = sourceSets.test.get().output.classesDirs
    classpath = sourceSets.test.get().runtimeClasspath

    jvmArgs("-Dotel.instrumentation.kubernetes-client.experimental-span-attributes=true")
    systemProperty("metadataConfig", "otel.instrumentation.kubernetes-client.experimental-span-attributes=true")
  }

  val testStableSemconv by registering(Test::class) {
    testClassesDirs = sourceSets.test.get().output.classesDirs
    classpath = sourceSets.test.get().runtimeClasspath

    jvmArgs("-Dotel.semconv-stability.opt-in=service.peer")
    systemProperty("metadataConfig", "otel.semconv-stability.opt-in=service.peer")
  }

  check {
    dependsOn(testExperimental, testStableSemconv)
  }
}
