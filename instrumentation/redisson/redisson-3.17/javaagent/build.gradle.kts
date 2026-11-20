import io.opentelemetry.instrumentation.gradle.executions

plugins {
  id("otel.javaagent-instrumentation")
  id("otel.testcontainers-testing")
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

testing.suites {
  register<JvmTestSuite>("redisson324Test") {
    sources {
      java {
        setSrcDirs(listOf("src/test/java"))
      }
    }

    dependencies {
      implementation(project(":instrumentation:redisson:redisson-common-3.0:testing"))
      // Redisson 3.24.3 routes configuration through ServiceManager.
      implementation("org.redisson:redisson:3.24.3")
    }
  }

  register<JvmTestSuite>("redisson344Test") {
    sources {
      java {
        setSrcDirs(listOf("src/redisson344Test/java"))
      }
    }

    dependencies {
      implementation(project(":instrumentation:redisson:redisson-common-3.0:testing"))
      implementation("org.redisson:redisson:3.44.0")
      implementation("io.netty:netty-transport-native-epoll:4.1.116.Final:linux-x86_64")
      implementation("org.testcontainers:testcontainers")
    }
  }

  executions("redisson324Test") {
    configureEach {
      filter {
        includeTestsMatching("*RedissonClientTest.configuredMasterSlaveServerTarget")
        includeTestsMatching("*RedissonClientTest.configuredSingleServerTarget")
      }
    }
  }

  executions("redisson344Test") {
    configureEach {
      filter {
        includeTestsMatching("*RedissonUnixSocketTest.*")
      }
    }
  }
}

tasks {
  withType<Test>().configureEach {
    systemProperty("testLatestDeps", otelProps.testLatestDeps)
  }

  check {
    dependsOn(testing.suites)
  }

  if (otelProps.denyUnsafe) {
    withType<Test>().configureEach {
      enabled = false
    }
  }
}
