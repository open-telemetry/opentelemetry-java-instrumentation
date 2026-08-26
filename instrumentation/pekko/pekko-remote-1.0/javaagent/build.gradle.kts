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
