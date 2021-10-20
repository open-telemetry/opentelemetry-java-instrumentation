plugins {
  id("otel.javaagent-instrumentation")
}

muzzle {
  pass {
    group.set("com.typesafe.play")
    module.set("play-ahc-ws-standalone_2.11")
    versions.set("[1.0.0,2.0.0)")
    assertInverse.set(true)
  }
  pass {
    group.set("com.typesafe.play")
    module.set("play-ahc-ws-standalone_2.12")
    versions.set("[1.0.0,2.0.0)")
    assertInverse.set(true)
  }
  fail {
    group.set("com.typesafe.play")
    module.set("play-ahc-ws-standalone_2.13")
    versions.set("[,]")
  }
}

val scalaVersion = "2.12"

dependencies {
  library("com.typesafe.play:play-ahc-ws-standalone_$scalaVersion:1.0.2")

  implementation(project(":instrumentation:play-ws:play-ws-common:javaagent"))

  testImplementation(project(":instrumentation:play-ws:play-ws-common:testing"))

  // These are to ensure cross compatibility
  testInstrumentation(project(":instrumentation:netty:netty-4.0:javaagent"))
  testInstrumentation(project(":instrumentation:netty:netty-4.1:javaagent"))
  testInstrumentation(project(":instrumentation:akka-http-10.0:javaagent"))
  testInstrumentation(project(":instrumentation:akka-actor-2.5:javaagent"))

  latestDepTestLibrary("com.typesafe.play:play-ahc-ws-standalone_$scalaVersion:1.+")
}
