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
}

otelJava {
  // AHC uses Unsafe and so does not run on later java version
  maxJavaVersionForTests.set(JavaVersion.VERSION_1_8)
}

// async-http-client 2.0.0 does not work with Netty versions newer than this due to referencing an
// internal file.
if (!(findProperty("testLatestDeps") as Boolean)) {
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
