import io.opentelemetry.instrumentation.gradle.executions

plugins {
  id("otel.javaagent-instrumentation")
  id("otel.testcontainers-testing")
}

muzzle {
  pass {
    group.set("org.redisson")
    module.set("redisson")
    versions.set("[3.0.0,3.17.0)")
    assertInverse.set(true)
  }
}

dependencies {
  library("org.redisson:redisson:3.0.0")

  implementation(project(":instrumentation:redisson:redisson-common-3.0:javaagent"))

  compileOnly("com.google.auto.value:auto-value-annotations")
  annotationProcessor("com.google.auto.value:auto-value")

  testInstrumentation(project(":instrumentation:redisson:redisson-3.17:javaagent"))

  testImplementation(project(":instrumentation:redisson:redisson-common-3.0:testing"))

  latestDepTestLibrary("org.redisson:redisson:3.16.+") // see redisson-3.17 module
}

testing {
  suites {
    // 3.6.5 is the last version where atomic mode is the seventh executeAsync argument.
    register<JvmTestSuite>("testRedisson365") {
      sources {
        java {
          setSrcDirs(listOf("src/test/java"))
          include("**/RedissonClientTest.java")
        }
      }
      dependencies {
        implementation("org.redisson:redisson:3.6.5")
        implementation(project(":instrumentation:redisson:redisson-common-3.0:testing"))
      }
    }

    // 3.7.1 is the last version where atomic mode is passed to executeAsync. It requires Java 8
    // because URIBuilder reflects on Field.modifiers during startup.
    register<JvmTestSuite>("testRedisson371") {
      sources {
        java {
          setSrcDirs(listOf("src/test/java"))
          include("**/RedissonClientTest.java")
        }
      }
      dependencies {
        implementation("org.redisson:redisson:3.7.1")
        implementation(project(":instrumentation:redisson:redisson-common-3.0:testing"))
      }
    }

    // Reactive and Rx batches supply options at execution time through 3.10.4.
    register<JvmTestSuite>("testRedisson3104") {
      dependencies {
        implementation("org.redisson:redisson:3.10.4")
        implementation("org.testcontainers:testcontainers")
      }
    }

    // BatchOptions.ExecutionMode was introduced in 3.7.2, but versions before 3.11.1 cannot run on
    // Java 21 because URIBuilder reflects on Field.modifiers. 3.11.1 is the earliest runnable
    // version that exercises RedissonPromiseWrapper; the latest version covered by this module uses
    // BatchPromise and bypasses the wrapper.
    register<JvmTestSuite>("testRedisson3111") {
      sources {
        java {
          setSrcDirs(listOf("src/test/java"))
          include("**/RedissonAsyncClientTest.java")
        }
      }
      dependencies {
        implementation("org.redisson:redisson:3.11.1")
        implementation(project(":instrumentation:redisson:redisson-common-3.0:testing"))
      }
    }

    executions("test") {
      configureEach {
        systemProperty("testLatestDeps", otelProps.testLatestDeps)
      }
    }

    executions("testRedisson365", "testRedisson371") {
      configureEach {
        enabled = otelProps.testJavaVersion == null || otelProps.testJavaVersion!!.isJava8
        systemProperty("testLatestDeps", true)
        javaLauncher.set(
          project.javaToolchains.launcherFor {
            languageVersion = JavaLanguageVersion.of(8)
          }
        )
      }
    }

    executions("testRedisson3104") {
      configureEach {
        enabled = otelProps.testJavaVersion == null || otelProps.testJavaVersion!!.isJava8
        javaLauncher.set(
          project.javaToolchains.launcherFor {
            languageVersion = JavaLanguageVersion.of(8)
          }
        )
      }
    }

    executions("testRedisson3111") {
      configureEach {
        systemProperty("testLatestDeps", true)
      }
    }
  }
}

tasks {
  check {
    dependsOn(testing.suites)
  }
}
