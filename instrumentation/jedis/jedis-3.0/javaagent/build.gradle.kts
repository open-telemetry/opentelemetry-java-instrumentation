import io.opentelemetry.instrumentation.gradle.executions

plugins {
  id("otel.javaagent-instrumentation")
}

muzzle {
  pass {
    group.set("redis.clients")
    module.set("jedis")
    versions.set("[3.0.0,4)")
    skip("jedis-3.6.2")
    assertInverse.set(true)
  }
}

dependencies {
  library("redis.clients:jedis:3.0.0")

  compileOnly("com.google.auto.value:auto-value-annotations")
  annotationProcessor("com.google.auto.value:auto-value")

  implementation(project(":instrumentation:jedis:jedis-common-1.4:javaagent"))

  testInstrumentation(project(":instrumentation:jedis:jedis-2.0:javaagent"))
  testInstrumentation(project(":instrumentation:jedis:jedis-4.0:javaagent"))

  latestDepTestLibrary("redis.clients:jedis:3.+") // see jedis-4.0 module
}

testing {
  suites {
    withType<JvmTestSuite>().configureEach {
      executions {
        register("PreviewSemconv") {
          jvmArgs("-Dotel.semconv-stability.preview=service.peer")
          systemProperty("metadataConfig", "otel.semconv-stability.preview=service.peer")
        }
        register("ExceptionSignalLogs") {
          jvmArgs("-Dotel.semconv.exception.signal.preview=logs")
        }
      }
    }

    register<JvmTestSuite>("jedis36Test") {
      sources.java.srcDir("src/test/java")

      dependencies {
        implementation("redis.clients:jedis:3.6.1")
        implementation("org.testcontainers:testcontainers")
      }
    }
  }
}

tasks {
  withType<Test>().configureEach {
    usesService(gradle.sharedServices.registrations["testcontainersBuildService"].service)
  }

  check {
    dependsOn(testing.suites)
  }
}
