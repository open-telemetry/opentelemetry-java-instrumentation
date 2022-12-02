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

  latestDepTestLibrary("org.scala-lang:scala-library:2.11.+")

  testImplementation(project(":instrumentation:executors:testing"))
}
