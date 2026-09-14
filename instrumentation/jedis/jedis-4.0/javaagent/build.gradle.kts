plugins {
  id("otel.javaagent-instrumentation")
}

muzzle {
  pass {
    group.set("redis.clients")
    module.set("jedis")
    versions.set("[4.0.0,)")
    assertInverse.set(true)
  }
}

dependencies {
  library("redis.clients:jedis:4.0.0")

  compileOnly("com.google.auto.value:auto-value-annotations")
  annotationProcessor("com.google.auto.value:auto-value")

  implementation(project(":instrumentation:jedis:jedis-common-1.4:javaagent"))

  testInstrumentation(project(":instrumentation:jedis:jedis-1.4:javaagent"))
  testInstrumentation(project(":instrumentation:jedis:jedis-2.0:javaagent"))
  testInstrumentation(project(":instrumentation:jedis:jedis-3.0:javaagent"))
}

testing {
  suites {
    register<JvmTestSuite>("jedis51Test") {
      sources {
        java {
          setSrcDirs(listOf("src/test/java"))
        }
      }

      dependencies {
        implementation("redis.clients:jedis:5.1.0")
        implementation("org.testcontainers:testcontainers")
      }
    }
  }
}

tasks {
  withType<Test>().configureEach {
    // latest dep test fails because peer ip is 0:0:0:0:0:0:0:1 instead of 127.0.0.1
    jvmArgs("-Djava.net.preferIPv4Stack=true")
    systemProperty("testLatestDeps", otelProps.testLatestDeps)
    systemProperty(
      "testPeriodicTopologyRefresh",
      otelProps.testLatestDeps || name.startsWith("jedis51"),
    )
    usesService(gradle.sharedServices.registrations["testcontainersBuildService"].service)
    systemProperty("collectMetadata", otelProps.collectMetadata)
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
