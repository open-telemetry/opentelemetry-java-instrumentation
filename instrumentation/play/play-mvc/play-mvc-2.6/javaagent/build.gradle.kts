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
  // TODO: Something about library configuration doesn't work well with scala compilation here.
  compileOnly("com.typesafe.play:play_$scalaVersion:$playVersion")

  testInstrumentation(project(":instrumentation:netty:netty-4.0:javaagent"))
  testInstrumentation(project(":instrumentation:netty:netty-4.1:javaagent"))
  testInstrumentation(project(":instrumentation:akka:akka-actor-2.3:javaagent"))
  testInstrumentation(project(":instrumentation:akka:akka-http-10.0:javaagent"))

  testImplementation("com.typesafe.play:play-java_$scalaVersion:$playVersion")
  testImplementation("com.typesafe.play:play-test_$scalaVersion:$playVersion")
}

testing {
  suites {
    val latestDepTest by registering(JvmTestSuite::class) {
      dependencies {
        implementation("com.typesafe.play:play-java_2.13:2.8.+")
        implementation("com.typesafe.play:play-test_2.13:2.8.+")
        implementation("com.typesafe.play:play-akka-http-server_2.13:2.8.+")
      }
    }
  }
}

val testLatestDeps = findProperty("testLatestDeps") as Boolean
tasks {
  if (testLatestDeps) {
    // disable regular test running and compiling tasks when latest dep test task is run
    named("test") {
      enabled = false
    }
    named("compileTestJava") {
      enabled = false
    }
  }

  check {
    dependsOn(testing.suites)
  }
}

// play-test depends on websocket-client
configurations.configureEach {
  exclude("org.eclipse.jetty.websocket", "websocket-client")
}
tasks.withType<Test>().configureEach {
  jvmArgs("-Dotel.instrumentation.common.experimental.controller-telemetry.enabled=true")
}
