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
  pass {
    group.set("com.typesafe.akka")
    module.set("akka-actor_2.12")
    versions.set("[2.5.0,)")
  }
  pass {
    group.set("com.typesafe.akka")
    module.set("akka-actor_2.13")
    versions.set("(,)")
  }
}

dependencies {
  bootstrap(project(":instrumentation:executors:bootstrap"))

  compileOnly("com.typesafe.akka:akka-actor_2.11:2.5.0")
  testImplementation("com.typesafe.akka:akka-actor_2.11:2.5.0")

  latestDepTestLibrary("com.typesafe.akka:akka-actor_2.13:+")
}

if (findProperty("testLatestDeps") as Boolean) {
  configurations {
    // akka artifact name is different for regular and latest tests
    testImplementation {
      exclude("com.typesafe.akka", "akka-actor_2.11")
    }
  }
}
