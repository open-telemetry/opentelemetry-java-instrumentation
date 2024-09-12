plugins {
  id("otel.library-instrumentation")
  id("org.graalvm.buildtools.native")
}

dependencies {
  compileOnly(project(":muzzle"))

  // pin the version strictly to avoid overriding by dependencyManagement versions
  compileOnly("ch.qos.logback:logback-classic") {
    version {
      // compiling against newer version than the earliest supported version (1.0.0) to support
      // features added in 1.3.0
      strictly("1.3.0")
    }
  }
  compileOnly("org.slf4j:slf4j-api") {
    version {
      strictly("2.0.0")
    }
  }

  if (findProperty("testLatestDeps") as Boolean) {
    testImplementation("ch.qos.logback:logback-classic:+")
  } else {
    testImplementation("ch.qos.logback:logback-classic") {
      version {
        strictly("1.0.0")
      }
    }
    testImplementation("org.slf4j:slf4j-api") {
      version {
        strictly("1.6.4")
      }
    }
  }

  testImplementation("io.opentelemetry:opentelemetry-sdk-testing")
}

graalvmNative {

  binaries.all {
    resources.autodetect()

    // Workaround for https://github.com/junit-team/junit5/issues/3405
    buildArgs.add("--initialize-at-build-time=org.junit.platform.launcher.core.LauncherConfig")
    buildArgs.add("--initialize-at-build-time=org.junit.jupiter.engine.config.InstantiatingConfigurationParameterConverter")
  }

  // See https://github.com/graalvm/native-build-tools/issues/572
  metadataRepository {
    enabled.set(false)
  }

  toolchainDetection.set(false)
}

// To be able to execute the tests as GraalVM native executables
configurations.configureEach {
  exclude("org.apache.groovy", "groovy")
  exclude("org.apache.groovy", "groovy-json")
  exclude("org.spockframework", "spock-core")
}

val latestDepTest = findProperty("testLatestDeps") as Boolean
testing {
  suites {
    val slf4j2ApiTest by registering(JvmTestSuite::class) {
      dependencies {
        implementation(project(":instrumentation:logback:logback-appender-1.0:library"))
        implementation("io.opentelemetry:opentelemetry-sdk-testing")
        implementation(project(":testing-common"))

        if (latestDepTest) {
          implementation("ch.qos.logback:logback-classic:+")
          implementation("org.slf4j:slf4j-api:+")
        } else {
          implementation("ch.qos.logback:logback-classic") {
            version {
              strictly("1.3.0")
            }
          }
          implementation("org.slf4j:slf4j-api") {
            version {
              strictly("2.0.0")
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

tasks.withType<Test>().configureEach {
  jvmArgs("-Dotel.instrumentation.common.experimental.controller-telemetry.enabled=true")
}
