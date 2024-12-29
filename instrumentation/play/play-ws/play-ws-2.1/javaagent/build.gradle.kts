plugins {
  id("otel.javaagent-instrumentation")
}

muzzle {

  fail {
    group.set("com.typesafe.play")
    module.set("play-ahc-ws-standalone_2.11")
    versions.set("[,]")
  }

  pass {
    group.set("com.typesafe.play")
    module.set("play-ahc-ws-standalone_2.12")
    versions.set("[2.1.0,]")
    skip("2.0.5") // muzzle passes but expecting failure, see play-ws-2.0-javaagent.gradle
    assertInverse.set(true)
  }

  pass {
    group.set("com.typesafe.play")
    module.set("play-ahc-ws-standalone_2.13")
    versions.set("[2.1.0,]")
    skip("2.0.5") // muzzle passes but expecting failure, see play-ws-2.0-javaagent.gradle
    assertInverse.set(true)
  }
}

val scalaVersion = "2.12"

dependencies {
  library("com.typesafe.play:play-ahc-ws-standalone_$scalaVersion:2.1.0")

  implementation(project(":instrumentation:play:play-ws:play-ws-common:javaagent"))

  testImplementation(project(":instrumentation:play:play-ws:play-ws-common:testing"))

  // These are to ensure cross compatibility
  testInstrumentation(project(":instrumentation:netty:netty-4.0:javaagent"))
  testInstrumentation(project(":instrumentation:netty:netty-4.1:javaagent"))
  testInstrumentation(project(":instrumentation:akka:akka-http-10.0:javaagent"))
  testInstrumentation(project(":instrumentation:akka:akka-actor-2.3:javaagent"))
}

val testLatestDeps = findProperty("testLatestDeps") as Boolean

testing {
  suites {
    val latestDepTest by registering(JvmTestSuite::class) {
      dependencies {
        implementation("com.typesafe.play:play-ahc-ws-standalone_2.13:+")
      }
    }
  }
}

tasks {
  if (testLatestDeps) {
    // disable regular test running and compiling tasks when latest dep test task is run
    named("test") {
      enabled = false
    }
  }

  named("latestDepTest") {
    enabled = testLatestDeps
  }

  check {
    dependsOn(testing.suites)
  }
}
