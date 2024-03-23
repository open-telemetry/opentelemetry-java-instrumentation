plugins {
  id("otel.javaagent-instrumentation")
  id("otel.scala-conventions")

  jacoco
}

muzzle {
  pass {
    group.set("com.typesafe.akka")
    module.set("akka-actor_2.11")
    versions.set("[2.5,)")
    assertInverse.set(true)
  }
}

dependencies {
  bootstrap(project(":instrumentation:executors:bootstrap"))

  library("com.typesafe.akka:akka-actor_2.11:2.5.0")

  testImplementation(project(":instrumentation:executors:testing"))
}

tasks.test {
  finalizedBy(tasks.jacocoTestReport)
}

tasks.jacocoTestReport {
  dependsOn(tasks.test)
}
