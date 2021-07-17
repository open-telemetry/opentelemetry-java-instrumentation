plugins {
  id("otel.javaagent-instrumentation")
  id("otel.scala-conventions")
}

muzzle {
  pass {
    group.set("com.typesafe.akka")
    module.set("akka-actor_2.11")
    versions.set("[2.5.0,)")
  }
}

dependencies {
  library("com.typesafe.akka:akka-actor_2.11:2.5.0")
}
