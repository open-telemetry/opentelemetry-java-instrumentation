import io.opentelemetry.instrumentation.gradle.executions
import io.opentelemetry.instrumentation.gradle.telemetryConfig

plugins {
  id("otel.javaagent-instrumentation")
}

val scalaVersion = "2.11"
val playVersion = "2.6.0"

muzzle {
  pass {
    group.set("com.typesafe.play")
    module.set("play_2.11")
    versions.set("[2.6.0,)")
    assertInverse.set(true)
  }
  pass {
    group.set("com.typesafe.play")
    module.set("play_2.12")
    versions.set("[2.6.0,)")
    assertInverse.set(true)
  }
  pass {
    group.set("com.typesafe.play")
    module.set("play_2.13")
    versions.set("[2.6.0,)")
    assertInverse.set(true)
  }
  pass {
    group.set("org.playframework")
    module.set("play_3")
    versions.set("(,)")
    assertInverse.set(true)
  }
}

dependencies {
  library("com.typesafe.play:play_$scalaVersion:$playVersion")

  testInstrumentation(project(":instrumentation:play:play-mvc:play-mvc-2.4:javaagent"))
  testInstrumentation(project(":instrumentation:netty:netty-4.0:javaagent"))
  testInstrumentation(project(":instrumentation:netty:netty-4.1:javaagent"))
  testInstrumentation(project(":instrumentation:akka:akka-actor-2.3:javaagent"))
  testInstrumentation(project(":instrumentation:akka:akka-http-10.0:javaagent"))
  testInstrumentation(project(":instrumentation:pekko:pekko-actor-1.0:javaagent"))
  testInstrumentation(project(":instrumentation:pekko:pekko-http-1.0:javaagent"))

  testLibrary("com.typesafe.play:play-java_$scalaVersion:$playVersion")
  testLibrary("com.typesafe.play:play-test_$scalaVersion:$playVersion")
}

val testJavaVersion = otelProps.testJavaVersion ?: JavaVersion.current()

testing.suites {
  named<JvmTestSuite>("test") {
    if (!otelProps.testLatestDeps && !testJavaVersion.isJava8) {
      tasks.named(sources.compileJavaTaskName) {
        enabled = false
      }
    }
  }

  register<JvmTestSuite>("play28Test") {
    dependencies {
      val version = baseVersion("2.8.0").orLatest("2.+")
      implementation("com.typesafe.play:play-java_2.13:$version")
      implementation("com.typesafe.play:play-test_2.13:$version")
      implementation("com.typesafe.play:play-akka-http-server_2.13:$version")
    }
  }

  register<JvmTestSuite>("play3Test") {
    if (testJavaVersion.isJava8) {
      tasks.named(sources.compileJavaTaskName) {
        enabled = false
      }
    }

    dependencies {
      val version = baseVersion("3.0.0").orLatest()
      implementation("org.playframework:play-java_3:$version")
      implementation("org.playframework:play-test_3:$version")
      implementation("org.playframework:play-pekko-http-server_3:$version")
    }
  }

  executions("test") {
    configureEach {
      // Play doesn't work with Java 9+ until 2.6.12
      if (!otelProps.testLatestDeps && !testJavaVersion.isJava8) {
        enabled = false
      }
    }
  }
  executions("play3Test") {
    configureEach {
      if (testJavaVersion.isJava8) {
        enabled = false
      }
    }
  }
}

tasks {
  withType<Test>().configureEach {
    telemetryConfig("otel.instrumentation.common.controller-telemetry.enabled", "true")
  }

  // Play doesn't work with Java 9+ until 2.6.12
  check {
    dependsOn(testing.suites)
  }

  if (otelProps.denyUnsafe) {
    withType<Test>().configureEach {
      enabled = false
    }
  }
}

// play-test depends on websocket-client
configurations.configureEach {
  exclude("org.eclipse.jetty.websocket", "websocket-client")
}
