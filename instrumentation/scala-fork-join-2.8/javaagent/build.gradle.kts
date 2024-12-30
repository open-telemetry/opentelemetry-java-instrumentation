plugins {
  id("otel.javaagent-instrumentation")
  id("otel.scala-conventions")
}

muzzle {
  pass {
    group.set("org.scala-lang")
    module.set("scala-library")
    versions.set("[2.8.0,2.12.0)")
    assertInverse.set(true)
  }
}

dependencies {
  bootstrap(project(":instrumentation:executors:bootstrap"))

  library("org.scala-lang:scala-library:2.8.0")

  // scala's ForkJoinPool was turned into an alias in scala 2.12 (which is why muzzle doesn't pass
  // on that version) and was removed completely in scala 2.13
  latestDepTestLibrary("org.scala-lang:scala-library:2.12.+") // no longer applicable

  testImplementation(project(":instrumentation:executors:testing"))
}
