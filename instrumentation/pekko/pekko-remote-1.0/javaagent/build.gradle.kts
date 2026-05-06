plugins {
  id("otel.javaagent-instrumentation")
  id("otel.scala-conventions")
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
  bootstrap(project(":instrumentation:executors:bootstrap"))

  library("org.apache.pekko:pekko-remote_2.12:1.0.1")

  latestDepTestLibrary("org.apache.pekko:pekko-remote_2.13:latest.release")

  testInstrumentation(project(":instrumentation:pekko:pekko-actor-1.0:javaagent"))

  testImplementation(project(":instrumentation:executors:testing"))
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
