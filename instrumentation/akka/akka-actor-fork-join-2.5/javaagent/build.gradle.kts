plugins {
  id("otel.javaagent-instrumentation")
  id("otel.scala-conventions")
}

muzzle {
  pass {
    group.set("com.typesafe.akka")
    module.set("akka-actor_2.11")
    versions.set("[2.5.0,2.6.0)") //Akka Fork Join Pool was removed in 2.6.0, replaced with java.util.concurrent implementation
  }
}

dependencies {
  bootstrap(project(":instrumentation:executors:bootstrap"))

  library("com.typesafe.akka:akka-actor_2.11:2.5.0")

  testImplementation(project(":instrumentation:executors:testing"))
}
