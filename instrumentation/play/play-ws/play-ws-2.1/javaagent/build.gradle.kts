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

  pass {
    group.set("org.playframework")
    module.set("play-ahc-ws-standalone_3")
    versions.set("[3.0.0,]")
    assertInverse.set(true)
  }
}

val scalaVersion = "2.12"

dependencies {
  library("com.typesafe.play:play-ahc-ws-standalone_$scalaVersion:2.1.0")

  implementation(project(":instrumentation:play:play-ws:play-ws-common-1.0:javaagent"))

  testImplementation(project(":instrumentation:play:play-ws:play-ws-common-1.0:testing"))

  // These are to ensure cross compatibility
  testInstrumentation(project(":instrumentation:netty:netty-4.0:javaagent"))
  testInstrumentation(project(":instrumentation:netty:netty-4.1:javaagent"))
  testInstrumentation(project(":instrumentation:akka:akka-http-10.0:javaagent"))
  testInstrumentation(project(":instrumentation:akka:akka-actor-2.3:javaagent"))
  testInstrumentation(project(":instrumentation:pekko:pekko-http-1.0:javaagent"))
  testInstrumentation(project(":instrumentation:pekko:pekko-actor-1.0:javaagent"))
}

testing {
  suites {
    val latestDepTest by registering(JvmTestSuite::class) {
      dependencies {
        implementation("com.typesafe.play:play-ahc-ws-standalone_2.13:latest.release")
      }
    }

    val play3Test by registering(JvmTestSuite::class) {
      dependencies {
        val version = baseVersion("3.0.0").orLatest()
        implementation("org.playframework:play-ahc-ws-standalone_3:$version")
      }
    }
  }
}

tasks {
  if (otelProps.testLatestDeps) {
    // disable regular test running and compiling tasks when latest dep test task is run
    named("test") {
      enabled = false
    }
  }

  named("latestDepTest") {
    enabled = otelProps.testLatestDeps
  }

  val testJavaVersion = otelProps.testJavaVersion ?: JavaVersion.current()
  if (testJavaVersion.isJava8) {
    named("play3Test") {
      enabled = false
    }
    named("compilePlay3TestJava") {
      enabled = false
    }
  }

  withType<Test>().configureEach {
    systemProperty("collectMetadata", otelProps.collectMetadata)
  }

  check {
    dependsOn(testing.suites)
  }

  if (otelProps.denyUnsafe) {
    withType<Test>().configureEach {
      enabled = false
    }
  }
}
