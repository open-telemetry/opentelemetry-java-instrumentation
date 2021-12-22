plugins {
  id("otel.javaagent-instrumentation")
}

muzzle {
  pass {
    group.set("com.typesafe.play")
    module.set("play_2.11")
    versions.set("[2.4.0,2.6)")
    assertInverse.set(true)
    // versions 2.3.9 and 2.3.10 depends on com.typesafe.netty:netty-http-pipelining:1.1.2
    // which does not exist
    skip("2.3.9", "2.3.10")
  }
  fail {
    group.set("com.typesafe.play")
    module.set("play_2.12")
    versions.set("[,]")
  }
  fail {
    group.set("com.typesafe.play")
    module.set("play_2.13")
    versions.set("[,]")
  }
}

otelJava {
  // Play doesn't work with Java 9+ until 2.6.12
  maxJavaVersionForTests.set(JavaVersion.VERSION_1_8)
}

dependencies {
  // TODO(anuraaga): Something about library configuration doesn't work well with scala compilation
  // here.
  compileOnly("com.typesafe.play:play_2.11:2.4.0")

  testInstrumentation(project(":instrumentation:netty:netty-4.0:javaagent"))
  testInstrumentation(project(":instrumentation:netty:netty-4.1:javaagent"))
  testInstrumentation(project(":instrumentation:akka:akka-http-10.0:javaagent"))
  testInstrumentation(project(":instrumentation:async-http-client:async-http-client-2.0:javaagent"))

  // Before 2.5, play used netty 3.x which isn't supported, so for better test consistency, we test with just 2.5
  testLibrary("com.typesafe.play:play-java_2.11:2.5.0")
  testLibrary("com.typesafe.play:play-java-ws_2.11:2.5.0")
  testLibrary("com.typesafe.play:play-test_2.11:2.5.0") {
    exclude("org.eclipse.jetty.websocket", "websocket-client")
  }

  latestDepTestLibrary("com.typesafe.play:play-java_2.11:2.5.+")
  latestDepTestLibrary("com.typesafe.play:play-java-ws_2.11:2.5.+")
  latestDepTestLibrary("com.typesafe.play:play-test_2.11:2.5.+") {
    exclude("org.eclipse.jetty.websocket", "websocket-client")
  }
}

// async-http-client 2.0 does not work with Netty versions newer than this due to referencing an
// internal file.
if (!(findProperty("testLatestDeps") as Boolean)) {
  configurations.configureEach {
    resolutionStrategy {
      eachDependency {
        // specifying a fixed version for all libraries with io.netty' group
        if (requested.group == "io.netty" && requested.name != "netty-bom" && requested.name != "netty") {
          useVersion("4.0.34.Final")
        }
      }
    }
  }
}
