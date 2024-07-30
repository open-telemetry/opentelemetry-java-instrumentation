plugins {
  id("otel.javaagent-instrumentation")
}

muzzle {
  pass {
    group.set("org.asynchttpclient")
    module.set("async-http-client")
    versions.set("[2.0.0,)")
    assertInverse.set(true)
  }
}

dependencies {
  library("org.asynchttpclient:async-http-client:2.0.0")

  testInstrumentation(project(":instrumentation:netty:netty-4.0:javaagent"))
  testInstrumentation(project(":instrumentation:netty:netty-4.1:javaagent"))
}

val latestDepTest = findProperty("testLatestDeps") as Boolean

if (latestDepTest) {
  otelJava {
    minJavaVersionSupported.set(JavaVersion.VERSION_11)
  }
} else {
  otelJava {
    // AHC uses Unsafe and so does not run on later java version
    maxJavaVersionForTests.set(JavaVersion.VERSION_1_8)
  }
}

tasks.withType<Test>().configureEach {
  systemProperty("testLatestDeps", latestDepTest)
}

// async-http-client 2.0.0 does not work with Netty versions newer than this due to referencing an
// internal file.
if (!latestDepTest) {
  configurations.configureEach {
    if (!name.contains("muzzle")) {
      resolutionStrategy {
        eachDependency {
          // specifying a fixed version for all libraries with io.netty' group
          if (requested.group == "io.netty" && requested.name != "netty-bom") {
            useVersion("4.0.34.Final")
          }
        }
      }
    }
  }
}
