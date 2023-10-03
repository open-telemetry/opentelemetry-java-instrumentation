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

  compileOnly("org.apache.pekko:pekko-actor_2.12:1.0.0") // first version in maven central
  testImplementation("org.apache.pekko:pekko-actor_2.12:1.0.0") // first version in maven central

  latestDepTestLibrary("org.apache.pekko:pekko-actor_2.13:+")
}
