plugins {
  id("otel.javaagent-instrumentation")
}

muzzle {
  pass {
    group.set("io.netty")
    module.set("netty")
    versions.set("[3.8.0.Final,4)")
    assertInverse.set(true)
  }
  fail {
    group.set("io.netty")
    module.set("netty-all")
    versions.set("[,]")
    excludeDependency("io.netty:netty-tcnative")
  }
}

dependencies {
  compileOnly("com.google.auto.value:auto-value-annotations")
  annotationProcessor("com.google.auto.value:auto-value")

  implementation(project(":instrumentation:netty:netty-common:library"))

  compileOnly("io.netty:netty:3.8.0.Final")

  testInstrumentation(project(":instrumentation:netty:netty-4.0:javaagent"))
  testInstrumentation(project(":instrumentation:netty:netty-4.1:javaagent"))

  testLibrary("io.netty:netty:3.8.0.Final")
  testLibrary("com.ning:async-http-client:1.8.0")

  latestDepTestLibrary("io.netty:netty:3.+") // see netty-4.0 module
  latestDepTestLibrary("com.ning:async-http-client:1.9.+") // see netty-4.0 module
}

// We need to force the dependency to the earliest supported version because other libraries declare newer versions.
if (!(findProperty("testLatestDeps") as Boolean)) {
  configurations.configureEach {
    if (!name.contains("muzzle")) {
      resolutionStrategy {
        eachDependency {
          // specifying a fixed version for all libraries with io.netty' group
          if (requested.group == "io.netty") {
            useVersion("3.8.0.Final")
          }
        }
      }
    }
  }
}
