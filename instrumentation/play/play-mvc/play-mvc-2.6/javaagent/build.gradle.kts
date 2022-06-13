plugins {
  id("otel.javaagent-instrumentation")
  id("org.unbroken-dome.test-sets")
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

testSets {
  // We need separate test sources to compile against latest Play.
  create("latestDepTest")
}

dependencies {
  // TODO(anuraaga): Something about library configuration doesn't work well with scala compilation
  // here.
  compileOnly("com.typesafe.play:play_$scalaVersion:$playVersion")

  testInstrumentation(project(":instrumentation:netty:netty-4.0:javaagent"))
  testInstrumentation(project(":instrumentation:netty:netty-4.1:javaagent"))
  testInstrumentation(project(":instrumentation:akka:akka-actor-2.5:javaagent"))
  testInstrumentation(project(":instrumentation:akka:akka-http-10.0:javaagent"))

  testImplementation("com.typesafe.play:play-java_$scalaVersion:$playVersion")
  testImplementation("com.typesafe.play:play-test_$scalaVersion:$playVersion") {
    exclude("org.eclipse.jetty.websocket", "websocket-client")
  }

  add("latestDepTestImplementation", "com.typesafe.play:play-java_2.13:2.8.+")
  add("latestDepTestImplementation", "com.typesafe.play:play-test_2.13:2.8.+") {
    exclude("org.eclipse.jetty.websocket", "websocket-client")
  }
  add("latestDepTestImplementation", "com.typesafe.play:play-akka-http-server_2.13:2.8.+")
}

tasks {
  if (findProperty("testLatestDeps") as Boolean) {
    // disable regular test running and compiling tasks when latest dep test task is run
    named("test") {
      enabled = false
    }
    named("compileTestGroovy") {
      enabled = false
    }
  }
}

if (findProperty("testLatestDeps") as Boolean) {
  configurations {
    // play artifact name is different for regular and latest tests
    testImplementation {
      exclude("com.typesafe.play", "play-java_2.11")
      exclude("com.typesafe.play", "play-test_2.11")
    }
  }
}
