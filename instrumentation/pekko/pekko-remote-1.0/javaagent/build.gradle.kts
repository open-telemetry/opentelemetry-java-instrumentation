import io.opentelemetry.instrumentation.gradle.executions

plugins {
  id("otel.javaagent-instrumentation")
}

muzzle {
  pass {
    group.set("org.apache.pekko")
    module.set("pekko-remote_2.12")
    versions.set("[1.0,)")
    assertInverse.set(true)
  }
  pass {
    group.set("org.apache.pekko")
    module.set("pekko-remote_2.13")
    versions.set("[1.0,)")
    assertInverse.set(true)
  }
  pass {
    group.set("org.apache.pekko")
    module.set("pekko-remote_3")
    versions.set("[1.0,)")
    assertInverse.set(true)
  }
}

dependencies {
  library("org.apache.pekko:pekko-remote_2.12:1.0.1")

  // classic remoting needs netty, which is an optional dependency of pekko-remote, pekko moved
  // the classic transport from netty 3 to netty 4 during the 1.x line
  if (otelProps.testLatestDeps) {
    testImplementation("io.netty:netty-transport:4.2.17.Final")
    testImplementation("io.netty:netty-handler:4.2.17.Final")
  } else {
    testImplementation("io.netty:netty:3.10.6.Final")
  }

  testInstrumentation(project(":instrumentation:pekko:pekko-actor-1.0:javaagent"))
  testInstrumentation(project(":instrumentation:executors:javaagent"))

  latestDepTestLibrary("org.apache.pekko:pekko-remote_2.13:latest.release")
}

testing.suites {
  // the agent matches pekko classes and their methods by name, run the tests against the scala 3
  // artifacts to catch a name that only holds for scala 2
  register<JvmTestSuite>("scala3Test") {
    sources {
      java.srcDir("src/test/java")
      resources.srcDir("src/test/resources")
    }

    dependencies {
      implementation("org.scala-lang:scala3-library_3:3.3.6")
      implementation("org.apache.pekko:pekko-remote_3:${baseVersion("1.0.1").orLatest()}")
      if (otelProps.testLatestDeps) {
        implementation("io.netty:netty-transport:4.2.17.Final")
        implementation("io.netty:netty-handler:4.2.17.Final")
      } else {
        implementation("io.netty:netty:3.10.6.Final")
      }
    }
  }

  // pekko 2.x has only milestone releases so far, which latest dep testing skips, run the tests
  // against them explicitly, pekko 2.x requires java 17 and has no scala 2.12 artifacts
  register<JvmTestSuite>("pekko2Test") {
    sources {
      java.srcDir("src/test/java")
      resources.srcDir("src/test/resources")
    }

    dependencies {
      implementation("org.apache.pekko:pekko-remote_2.13:${baseVersion("2.0.0-M4").orLatest("2.+")}")
      implementation("io.netty:netty-transport:4.2.17.Final")
      implementation("io.netty:netty-handler:4.2.17.Final")
    }
  }

  register<JvmTestSuite>("pekko2Scala3Test") {
    sources {
      java.srcDir("src/test/java")
      resources.srcDir("src/test/resources")
    }

    dependencies {
      implementation("org.scala-lang:scala3-library_3:3.3.8")
      implementation("org.apache.pekko:pekko-remote_3:${baseVersion("2.0.0-M4").orLatest("2.+")}")
      implementation("io.netty:netty-transport:4.2.17.Final")
      implementation("io.netty:netty-handler:4.2.17.Final")
    }
  }

  executions("test", "scala3Test") {}

  val testJavaVersion = otelProps.testJavaVersion ?: JavaVersion.current()
  executions("pekko2Test", "pekko2Scala3Test") {
    configureEach {
      // agrona, used by artery in pekko 2.x, accesses jdk internals
      jvmArgs("--add-opens=java.base/jdk.internal.misc=ALL-UNNAMED")
      if (!testJavaVersion.isCompatibleWith(JavaVersion.VERSION_17)) {
        enabled = false
      }
    }
  }
}

tasks {
  check {
    dependsOn(testing.suites)
  }
}

if (otelProps.testLatestDeps) {
  configurations {
    // pekko artifact name is different for regular and latest tests
    testImplementation {
      exclude("org.apache.pekko", "pekko-remote_2.12")
    }
  }
}

if (otelProps.denyUnsafe) {
  tasks.withType<Test>().configureEach {
    enabled = false
  }
}
