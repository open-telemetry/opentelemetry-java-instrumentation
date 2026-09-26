plugins {
  id("otel.javaagent-instrumentation")
  id("otel.scala-conventions")
  id("otel.nullaway-conventions")
}

muzzle {
  pass {
    group.set("com.typesafe.akka")
    module.set("akka-actor_2.11")
    versions.set("[2.3,)")
    assertInverse.set(true)
    excludeInstrumentationName("akka-actor-2.5-forkjoin")
  }
  pass {
    group.set("com.typesafe.akka")
    module.set("akka-actor_2.12")
    versions.set("[2.3,)")
    assertInverse.set(true)
    excludeInstrumentationName("akka-actor-2.5-forkjoin")
  }
  pass {
    group.set("com.typesafe.akka")
    module.set("akka-actor_2.13")
    versions.set("[2.3,)")
    assertInverse.set(true)
    excludeInstrumentationName("akka-actor-2.5-forkjoin")
  }

  // Akka's fork-join was removed in 2.6, replaced with the normal java.concurrent version
  pass {
    // instrumentation-docs:ignore - verification only, the directives above are the ranges we document
    name.set("Akka actor 2.5 forkjoin instrumentation for Scala 2.11")
    group.set("com.typesafe.akka")
    module.set("akka-actor_2.11")
    versions.set("[2.5,)") // Scala 2.11 support was dropped after 2.5, so no 2.6 versions exist with this name
    assertInverse.set(true)
    excludeInstrumentationName("akka-actor-2.3-core")
  }
  pass {
    // instrumentation-docs:ignore - verification only, the directives above are the ranges we document
    name.set("Akka actor 2.5 forkjoin instrumentation for Scala 2.12")
    group.set("com.typesafe.akka")
    module.set("akka-actor_2.12")
    versions.set("[2.5,2.6)")
    assertInverse.set(true)
    excludeInstrumentationName("akka-actor-2.3-core")
  }
  pass {
    // instrumentation-docs:ignore - verification only, the directives above are the ranges we document
    name.set("Akka actor 2.5 forkjoin instrumentation for Scala 2.13")
    group.set("com.typesafe.akka")
    module.set("akka-actor_2.13")
    versions.set("[2.5.23,2.6)") // Scala 2.13 support was added in the middle of the 2.5 release
    assertInverse.set(true)
    excludeInstrumentationName("akka-actor-2.3-core")
  }
}

dependencies {
  bootstrap(project(":instrumentation:executors:bootstrap"))

  compileOnly("com.typesafe.akka:akka-actor_2.11:2.3.2") // first version in maven central
  compileOnly("com.typesafe.akka:akka-actor_2.11:2.5.0") // for akka.dispatch.forkjoin types
  testImplementation("com.typesafe.akka:akka-actor_2.11:2.3.2") // first version in maven central

  latestDepTestLibrary("com.typesafe.akka:akka-actor_2.13:latest.release")
}

testing {
  suites {
    register<JvmTestSuite>("forkJoinTest") {
      dependencies {
        val scalaVersion = if (otelProps.testLatestDeps) "2.13" else "2.11"
        implementation(
          "com.typesafe.akka:akka-actor_$scalaVersion:${baseVersion("2.5.0").orLatest("2.5.+")}",
        )
        implementation(project(":instrumentation:executors:testing"))
      }
    }
  }
}

tasks {
  check {
    dependsOn(testing.suites)
  }

  if (otelProps.denyUnsafe) {
    withType<Test>().configureEach {
      enabled = false
    }
  }
}

if (otelProps.testLatestDeps) {
  configurations {
    // akka artifact name is different for regular and latest tests
    testImplementation {
      exclude("com.typesafe.akka", "akka-actor_2.11")
    }
  }
}
