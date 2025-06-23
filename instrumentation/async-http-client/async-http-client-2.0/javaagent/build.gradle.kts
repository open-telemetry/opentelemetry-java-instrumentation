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
val testJavaVersion =
  gradle.startParameter.projectProperties["testJavaVersion"]?.let(JavaVersion::toVersion)
    ?: JavaVersion.current()

if (!latestDepTest) {
  otelJava {
    // AHC uses Unsafe and so does not run on later java version
    maxJavaVersionForTests.set(JavaVersion.VERSION_1_8)
  }
}

tasks.withType<Test>().configureEach {
  systemProperty("testLatestDeps", latestDepTest)
  // async-http-client 3.0 requires java 11
  // We are not using minJavaVersionSupported for latestDepTest because that way the instrumentation
  // gets compiled with java 11 when running latestDepTest. This causes play-mvc-2.4 latest dep tests
  // to fail because they require java 8 and instrumentation compiled with java 11 won't apply.
  if (latestDepTest && testJavaVersion.isJava8) {
    enabled = false
  }
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
