plugins {
  id("otel.javaagent-instrumentation")
  id("otel.scala-conventions")
}

muzzle {
  pass {
    group.set("org.apache.pekko")
    module.set("pekko-actor_2.12")
    versions.set("[1.0,)")
    assertInverse.set(true)
  }
  pass {
    group.set("org.apache.pekko")
    module.set("pekko-actor_2.13")
    versions.set("[1.0,)")
    assertInverse.set(true)
  }
  pass {
    group.set("org.apache.pekko")
    module.set("pekko-actor_3")
    versions.set("[1.0,)")
    assertInverse.set(true)
  }
}

dependencies {
  bootstrap(project(":instrumentation:executors:bootstrap"))

  library("org.apache.pekko:pekko-actor_2.12:1.0.1")

  latestDepTestLibrary("org.apache.pekko:pekko-actor_2.13:+")

  testImplementation(project(":instrumentation:executors:testing"))
}

if (findProperty("testLatestDeps") as Boolean) {
  configurations {
    // pekko artifact name is different for regular and latest tests
    testImplementation {
      exclude("org.apache.pekko", "pekko-actor_2.12")
    }
  }
}
