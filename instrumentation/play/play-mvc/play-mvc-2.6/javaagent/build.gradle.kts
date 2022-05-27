plugins {
  id("otel.javaagent-instrumentation")
}

val scalaVersion = "2.11"
val playVersion = "2.6.0"

muzzle {
  pass {
    group.set("com.typesafe.play")
    module.set("play_$scalaVersion")
    versions.set("[$playVersion,)")
    assertInverse.set(true)
    // versions 2.3.9 and 2.3.10 depends on com.typesafe.netty:netty-http-pipelining:1.1.2
    // which does not exist
    skip("2.3.9", "2.3.10")
  }
  pass {
    group.set("com.typesafe.play")
    module.set("play_2.12")
    versions.set("[$playVersion,)")
    assertInverse.set(true)
  }
  pass {
    group.set("com.typesafe.play")
    module.set("play_2.13")
    versions.set("[$playVersion,)")
    assertInverse.set(true)
  }
}

otelJava {
  // Play doesn't work with Java 9+ until 2.6.12
  maxJavaVersionForTests.set(JavaVersion.VERSION_1_8)
}

dependencies {
  // TODO(anuraaga): Something about library configuration doesn't work well with scala compilation
  // here.
  compileOnly("com.typesafe.play:play_$scalaVersion:$playVersion")

  testInstrumentation(project(":instrumentation:netty:netty-4.0:javaagent"))
  testInstrumentation(project(":instrumentation:netty:netty-4.1:javaagent"))
  testInstrumentation(project(":instrumentation:akka:akka-actor-2.5:javaagent"))
  testInstrumentation(project(":instrumentation:akka:akka-http-10.0:javaagent"))

  testLibrary("com.typesafe.play:play-java_$scalaVersion:$playVersion")
  // TODO: Play WS is a separately versioned library starting with 2.6 and needs separate instrumentation.
  testLibrary("com.typesafe.play:play-test_$scalaVersion:$playVersion") {
    exclude("org.eclipse.jetty.websocket", "websocket-client")
  }

  // TODO: This should be changed to the latest in scala 2.13 instead of 2.11 since its ahead
  latestDepTestLibrary("com.typesafe.play:play-java_$scalaVersion:2.+")
  latestDepTestLibrary("com.typesafe.play:play-test_$scalaVersion:2.+") {
    exclude("org.eclipse.jetty.websocket", "websocket-client")
  }
}
